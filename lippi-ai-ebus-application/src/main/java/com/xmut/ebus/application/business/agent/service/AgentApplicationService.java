package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.command.StartPicklistRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.dto.PicklistRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.sse.PiEventToAd4Mapper;
import com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin;
import com.xmut.ebus.application.business.agent.support.CreditHoldSupport;
import com.xmut.ebus.application.business.agent.support.PersistedGenerationArtifact;
import com.xmut.ebus.application.business.agent.support.SkillRunProfile;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.ViewProjectContext;
import com.xmut.ebus.application.business.picklist.support.PicklistArtifactParser;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.ObjectUtils;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.model.SessionSceneBinding;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.agent.repository.PiSessionSceneRepository;
import com.xmut.ebus.domain.business.scene.constant.SceneStatus;
import com.xmut.ebus.domain.business.scene.model.Scene;
import com.xmut.ebus.domain.business.scene.repository.SceneRepository;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AgentRuntime 编排：预占 → GenerationRun → AgentSession → AD-4 SSE。
 * <p>
 * 空跑只 {@code reserveOne} + 结束 {@code release}；禁止 {@code settle}；
 * 不发 {@code artifact_ready}/{@code run_settled}。
 * 通用 Generation 管道：{@link #streamGenerationRun}；dry 永不 settle；
 * 计费：persist 插件 → {@link ComputerViewResolver} → settle。
 * 计费生成必须绑定 AVAILABLE 场景（AD-15）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    static final String EMPTY_RUN_FAIL_REASON = "空跑无可用成果，预占已释放";
    static final String RELEASE_FAILED_REASON = "空跑结束但预占释放失败";
    static final String SSE_SEND_FAILED_RELEASED = "SSE 下发失败，预占已释放";
    static final String SSE_SEND_FAILED_RELEASE_FAILED = "SSE 下发失败且预占释放失败";

    static final String PICKLIST_RELEASE_FAILED = "选品结束但预占释放失败";
    static final String PICKLIST_MODEL_FAILED = "选品生成失败，请稍后重试";
    static final String PICKLIST_SETTLE_FAILED = "选品成果已生成但结算失败，请联系支持";

    static final String MSG_SCENE_REQUIRED = "请先选择场景";
    static final String MSG_SCENE_NOT_FOUND = "场景不存在";
    static final String MSG_SCENE_NOT_OPEN = "该场景尚未开放";
    static final String MSG_SCENE_MISMATCH = "场景编号与场景码不一致";
    static final String MSG_SESSION_SCENE_MISMATCH = "当前会话已绑定其他场景";
    static final String MSG_PROMPT_REQUIRED = "请先输入内容";

    private final CreditHoldSupport creditHoldSupport;
    private final GenerationRunRepository generationRunRepository;
    private final PiSessionSceneRepository piSessionSceneRepository;
    private final SceneRepository sceneRepository;
    private final SceneCapabilityPackLoader sceneCapabilityPackLoader;
    private final AgentSession agentSession;
    private final List<ArtifactPersistPlugin> artifactPersistPlugins;
    private final ComputerViewResolver computerViewResolver;
    private final Clock clock;

    /**
     * 一站式：预占建 Run 后立刻流式（便于单测）。积分不足时不调 sink。
     */
    public EmptyRunContext startEmptyRun(StartEmptyRunCommand command, Consumer<Ad4SseEvent> sink) {
        EmptyRunContext context = prepareEmptyRun(command);
        streamEmptyRun(context, sink);
        return context;
    }

    /**
     * 一站式计费选品（便于单测）。
     */
    public PicklistRunContext startPicklistRun(StartPicklistRunCommand command, Consumer<Ad4SseEvent> sink) {
        PicklistRunContext context = preparePicklistRun(command);
        streamPicklistRun(context, sink);
        return context;
    }

    /**
     * 通用：场景绑定 + 预占 + GenerationRun。
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerationRunContext prepareGenerationRun(StartGenerationRunCommand command) {
        ObjectUtils.requireNonNull(command, "生成命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");
        SkillRunProfile profile = SkillRunProfile.resolve(command.getSkillId(), command.isDryRun());
        String promptText;
        if (profile.isRequireUserText()) {
            promptText = StringUtils.requireHasText(command.getText(), MSG_PROMPT_REQUIRED).trim();
        } else if (StringUtils.hasText(command.getText())) {
            promptText = command.getText().trim();
        } else {
            promptText = "empty-run";
        }

        Scene scene = resolveAvailableScene(command.getSceneId(), command.getSceneCode());
        String sessionId = StringUtils.hasText(command.getSessionId())
                ? command.getSessionId().trim()
                : UUID.randomUUID().toString();
        bindSessionScene(sessionId, scene);

        String holdId = reserveOne(userId);
        Instant now = Instant.now(clock);
        String runId = UUID.randomUUID().toString();
        GenerationRun run = GenerationRun.start(
                runId, userId, holdId, sessionId, scene.getId(), scene.getSceneCode(), now);
        generationRunRepository.save(run);

        LoggerUtils.success(log, AgentApplicationService.class, "prepareGenerationRun",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("holdId", holdId),
                NameValue.create("sessionId", sessionId),
                NameValue.create("sceneId", scene.getId()),
                NameValue.create("sceneCode", scene.getSceneCode()),
                NameValue.create("skillId", profile.getSkillId()),
                NameValue.create("skillBound", profile.isSkillBound()),
                NameValue.create("dryRun", profile.isDryRun()));
        return new GenerationRunContext(runId, userId, holdId, sessionId, scene.getSceneCode(), promptText, profile);
    }

    /**
     * 空跑适配：dry profile。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmptyRunContext prepareEmptyRun(StartEmptyRunCommand command) {
        ObjectUtils.requireNonNull(command, "空跑命令不能为空");
        GenerationRunContext gen = prepareGenerationRun(StartGenerationRunCommand.builder()
                .userId(command.getUserId())
                .username(command.getUsername())
                .sessionId(command.getSessionId())
                .sceneId(command.getSceneId())
                .sceneCode(command.getSceneCode())
                .dryRun(true)
                .build());
        return new EmptyRunContext(gen.getRunId(), gen.getUserId(), gen.getHoldId(),
                gen.getSessionId(), gen.getSceneCode());
    }

    /**
     * 计费选品适配：ecommerce-picklist profile。
     */
    @Transactional(rollbackFor = Exception.class)
    public PicklistRunContext preparePicklistRun(StartPicklistRunCommand command) {
        ObjectUtils.requireNonNull(command, "选品命令不能为空");
        GenerationRunContext gen = prepareGenerationRun(StartGenerationRunCommand.builder()
                .userId(command.getUserId())
                .username(command.getUsername())
                .text(command.getText())
                .sessionId(command.getSessionId())
                .sceneId(command.getSceneId())
                .sceneCode(command.getSceneCode())
                .skillId(SceneCapabilityPackLoader.SKILL_PICKLIST)
                .dryRun(false)
                .build());
        return new PicklistRunContext(gen.getRunId(), gen.getUserId(), gen.getHoldId(),
                gen.getSessionId(), gen.getSceneCode(), gen.getPromptText());
    }

    /**
     * 解析 sceneId / sceneCode → AVAILABLE 场景；缺省/未知/冲突/灰卡拒绝。
     */
    Scene resolveAvailableScene(String sceneId, String sceneCode) {
        boolean hasId = StringUtils.hasText(sceneId);
        boolean hasCode = StringUtils.hasText(sceneCode);
        if (!hasId && !hasCode) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_REQUIRED);
        }

        if (hasId && hasCode) {
            Optional<Scene> byId = sceneRepository.findByBizId(sceneId.trim());
            Optional<Scene> byCode = sceneRepository.findBySceneCode(sceneCode.trim());
            if (!byId.isPresent() || !byCode.isPresent()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_NOT_FOUND);
            }
            Scene left = byId.get();
            Scene right = byCode.get();
            if (!left.getId().equals(right.getId())
                    || !left.getSceneCode().equals(right.getSceneCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_MISMATCH);
            }
            return requireAvailable(left);
        }

        if (hasId) {
            Scene scene = sceneRepository.findByBizId(sceneId.trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_NOT_FOUND));
            return requireAvailable(scene);
        }

        Scene scene = sceneRepository.findBySceneCode(sceneCode.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_NOT_FOUND));
        return requireAvailable(scene);
    }

    private static Scene requireAvailable(Scene scene) {
        if (scene.getStatus() != SceneStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SCENE_NOT_OPEN);
        }
        return scene;
    }

    private void bindSessionScene(String sessionId, Scene scene) {
        Optional<SessionSceneBinding> existing = piSessionSceneRepository.findBySessionId(sessionId);
        if (existing.isPresent() && existing.get().hasScene()) {
            SessionSceneBinding bound = existing.get();
            if (!scene.getId().equals(bound.getSceneId())
                    || !scene.getSceneCode().equals(bound.getSceneCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SESSION_SCENE_MISMATCH);
            }
            return;
        }
        try {
            piSessionSceneRepository.ensureBound(sessionId, scene.getId(), scene.getSceneCode());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SESSION_SCENE_MISMATCH);
        }
    }

    private String reserveOne(String userId) {
        return creditHoldSupport.reserveOne(userId);
    }

    /**
     * 空跑适配 → {@link #streamGenerationRun}。
     */
    public void streamEmptyRun(EmptyRunContext context, Consumer<Ad4SseEvent> sink) {
        streamGenerationRun(GenerationRunContext.fromEmpty(context, SkillRunProfile.dry(null)), sink);
    }

    /**
     * 选品适配 → {@link #streamGenerationRun}。
     */
    public void streamPicklistRun(PicklistRunContext context, Consumer<Ad4SseEvent> sink) {
        streamGenerationRun(GenerationRunContext.fromPicklist(context, SkillRunProfile.billedPicklist()), sink);
    }

    /**
     * 通用 Generation SSE：dry → run_failed；无 Skill → markdown view + release；计费 → persist → settle。
     */
    public void streamGenerationRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(context, "生成上下文不能为空");
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");
        ObjectUtils.requireNonNull(context.getProfile(), "SkillRunProfile 不能为空");

        if (context.getProfile().isDryRun()) {
            streamDryRun(context, sink);
            return;
        }
        if (!context.getProfile().isSkillBound()) {
            streamNoSkillRun(context, sink);
            return;
        }
        streamBilledRun(context, sink);
    }

    /**
     * 无 Skill：prompt(none) → NoSkillMarkdown → artifact_ready(view) → release（不 settle、不 run_failed）。
     */
    private void streamNoSkillRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        AtomicBoolean hasMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean holdClosed = false;
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_started, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            try {
                sceneCapabilityPackLoader.load(context.getSceneCode());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink, StringUtils.hasText(ex.getMessage())
                        ? ex.getMessage()
                        : SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
                return;
            }

            subscription = agentSession.subscribe(progressListener(
                    context.getRunId(), hasMessageDelta, aborted, sink, "streamGenerationRun"));

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text(context.getPromptText())
                    .skillId(null)
                    .build());

            logModelUsagePlaceholder(context, result);

            if (aborted.get()) {
                holdClosed = finishFailed(context, sink, SSE_SEND_FAILED_RELEASED);
                return;
            }

            if (result == null || result.getStatus() != TurnResult.Status.OK) {
                String reason = result != null && StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : PICKLIST_MODEL_FAILED;
                holdClosed = finishFailed(context, sink, reason);
                return;
            }

            String finalText = result.getFinalResponse();
            if (!hasMessageDelta.get() && StringUtils.hasText(finalText)) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", finalText);
                emit(sink, Ad4SseEvent.of(Ad4EventName.message_delta, delta));
            }

            final Map<String, Object> projectedView;
            try {
                projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                        .skillBound(false)
                        .finalResponse(finalText)
                        .build());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ComputerViewResolver.MSG_VIEW_UNAVAILABLE);
                return;
            }

            Map<String, Object> ready = new LinkedHashMap<String, Object>();
            ready.put("view", projectedView);
            try {
                emit(sink, Ad4SseEvent.of(Ad4EventName.artifact_ready, ready));
            } catch (RuntimeException emitEx) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                        emitEx.getMessage() != null ? emitEx.getMessage() : "SSE emit artifact_ready failed",
                        NameValue.create("runId", context.getRunId()));
            }

            boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
            holdClosed = true;
            markRunFailed(context.getRunId());
            if (!releaseOk) {
                emitRunFailed(sink, PICKLIST_RELEASE_FAILED, false);
            }

            LoggerUtils.success(log, AgentApplicationService.class, "streamGenerationRun",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("skillBound", false));
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (!holdClosed) {
                finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : PICKLIST_MODEL_FAILED);
            }
        } finally {
            closeQuietly(subscription);
        }
    }

    private void streamDryRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        AtomicBoolean hasMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean released = false;
        SkillRunProfile profile = context.getProfile();
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_started, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            final SceneCapabilityPack pack;
            try {
                pack = sceneCapabilityPackLoader.load(context.getSceneCode());
            } catch (BusinessException ex) {
                boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
                released = releaseOk;
                markRunFailed(context.getRunId());
                String reason = releaseOk
                        ? (StringUtils.hasText(ex.getMessage())
                        ? ex.getMessage()
                        : SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE)
                        : RELEASE_FAILED_REASON;
                emitRunFailed(sink, reason, true);
                return;
            }
            if (!pack.hasSkill(profile.getSkillId())) {
                boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
                released = releaseOk;
                markRunFailed(context.getRunId());
                emitRunFailed(sink, releaseOk
                        ? SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE
                        : RELEASE_FAILED_REASON, true);
                return;
            }

            subscription = agentSession.subscribe(progressListener(context.getRunId(), hasMessageDelta, aborted, sink, "streamGenerationRun"));

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text(context.getPromptText())
                    .skillId(profile.getSkillId())
                    .build());

            if (aborted.get()) {
                boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
                markRunFailed(context.getRunId());
                emitRunFailed(sink, releaseOk ? SSE_SEND_FAILED_RELEASED : SSE_SEND_FAILED_RELEASE_FAILED, true);
                return;
            }

            if (!hasMessageDelta.get()) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", result != null && StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : "empty-run stub");
                emit(sink, Ad4SseEvent.of(Ad4EventName.message_delta, delta));
            }

            boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
            released = releaseOk;
            markRunFailed(context.getRunId());
            emitRunFailed(sink, releaseOk ? EMPTY_RUN_FAIL_REASON : RELEASE_FAILED_REASON, true);
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (!released) {
                boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
                released = releaseOk;
                markRunFailed(context.getRunId());
                if (releaseOk) {
                    emitRunFailed(sink, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "生成失败", true);
                } else {
                    emitRunFailed(sink, RELEASE_FAILED_REASON, true);
                }
            } else {
                markRunFailed(context.getRunId());
                emitRunFailed(sink, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "生成失败", true);
            }
        } finally {
            closeQuietly(subscription);
        }
    }

    private void streamBilledRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        AtomicBoolean hasMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean holdClosed = false;
        boolean settledOk = false;
        SkillRunProfile profile = context.getProfile();
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_started, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            final SceneCapabilityPack pack;
            try {
                pack = sceneCapabilityPackLoader.load(context.getSceneCode());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink, StringUtils.hasText(ex.getMessage())
                        ? ex.getMessage()
                        : SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
                return;
            }
            if (!pack.hasSkill(profile.getSkillId())) {
                holdClosed = finishFailed(context, sink, SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
                return;
            }

            subscription = agentSession.subscribe(progressListener(context.getRunId(), hasMessageDelta, aborted, sink, "streamGenerationRun"));

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text(context.getPromptText())
                    .skillId(profile.getSkillId())
                    .build());

            logModelUsagePlaceholder(context, result);

            if (aborted.get()) {
                holdClosed = finishFailed(context, sink, SSE_SEND_FAILED_RELEASED);
                return;
            }

            if (result == null || result.getStatus() != TurnResult.Status.OK) {
                String reason = result != null && StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : PICKLIST_MODEL_FAILED;
                holdClosed = finishFailed(context, sink, reason);
                return;
            }

            if (!hasMessageDelta.get() && StringUtils.hasText(result.getFinalResponse())) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", result.getFinalResponse());
                emit(sink, Ad4SseEvent.of(Ad4EventName.message_delta, delta));
            }

            final PersistedGenerationArtifact persisted;
            try {
                ArtifactPersistPlugin plugin = requirePersistPlugin(profile.getPersistAs());
                persisted = plugin.persist(
                        context.getUserId(), context.getRunId(), context.getSceneCode(), result.getFinalResponse());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : PicklistArtifactParser.MSG_UNUSABLE);
                return;
            }

            final Map<String, Object> projectedView;
            try {
                projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                        .skillBound(true)
                        .finalResponse(result.getFinalResponse())
                        .rawView(persisted.getRawView())
                        .artifact(persisted.getArtifact())
                        .build());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ComputerViewResolver.MSG_VIEW_UNAVAILABLE);
                return;
            }

            try {
                creditHoldSupport.settle(context.getUserId(), context.getHoldId());
            } catch (BusinessException ex) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                        ex.getMessage() != null ? ex.getMessage() : "settle failed",
                        NameValue.create("runId", context.getRunId()),
                        NameValue.create("holdId", context.getHoldId()));
                markRunFailed(context.getRunId());
                emitRunFailed(sink, PICKLIST_SETTLE_FAILED, false);
                holdClosed = true;
                return;
            }

            markRunSettled(context.getRunId(), persisted.getArtifactRef());
            settledOk = true;
            holdClosed = true;

            try {
                emit(sink, Ad4SseEvent.of(Ad4EventName.artifact_ready,
                        toArtifactReady(persisted, projectedView)));
                emit(sink, Ad4SseEvent.of(Ad4EventName.run_settled,
                        toRunSettled(context, persisted.getArtifactRef())));
            } catch (RuntimeException emitEx) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                        emitEx.getMessage() != null ? emitEx.getMessage() : "SSE emit after settle failed",
                        NameValue.create("runId", context.getRunId()),
                        NameValue.create("artifactRef", persisted.getArtifactRef()));
            }

            LoggerUtils.success(log, AgentApplicationService.class, "streamGenerationRun",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("artifactRef", persisted.getArtifactRef()),
                    NameValue.create("skillId", profile.getSkillId()));
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (settledOk) {
                return;
            }
            if (!holdClosed) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : PICKLIST_MODEL_FAILED);
            } else {
                markRunFailed(context.getRunId());
                emitRunFailed(sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : PICKLIST_MODEL_FAILED, false);
            }
        } finally {
            closeQuietly(subscription);
        }
    }

    /**
     * NFR2：单次 Run 用量/成本可观测（近端占位字段；真实 token 待 TurnResult 贯通后替换）。
     */
    private void logModelUsagePlaceholder(GenerationRunContext context, TurnResult result) {
        Integer responseChars = result != null && result.getFinalResponse() != null
                ? Integer.valueOf(result.getFinalResponse().length())
                : null;
        String status = result != null && result.getStatus() != null
                ? result.getStatus().name()
                : "UNKNOWN";
        LoggerUtils.success(log, AgentApplicationService.class, "modelUsage",
                NameValue.create("runId", context.getRunId()),
                NameValue.create("sessionId", context.getSessionId()),
                NameValue.create("skillId", context.getProfile().getSkillId()),
                NameValue.create("turnStatus", status),
                NameValue.create("promptTokens", null),
                NameValue.create("completionTokens", null),
                NameValue.create("totalTokens", null),
                NameValue.create("estimatedCost", null),
                NameValue.create("responseChars", responseChars));
    }

    private boolean finishFailed(GenerationRunContext context, Consumer<Ad4SseEvent> sink, String reason) {
        boolean releaseOk = creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
        markRunFailed(context.getRunId());
        emitRunFailed(sink, releaseOk ? reason : PICKLIST_RELEASE_FAILED, false);
        return true;
    }

    private ArtifactPersistPlugin requirePersistPlugin(String persistAs) {
        if (artifactPersistPlugins != null) {
            for (ArtifactPersistPlugin plugin : artifactPersistPlugins) {
                if (plugin != null && persistAs.equals(plugin.persistAs())) {
                    return plugin;
                }
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "未注册成果落库插件: " + persistAs);
    }

    private Consumer<PiEvent> progressListener(final String runId,
                                               final AtomicBoolean hasMessageDelta,
                                               final AtomicBoolean aborted,
                                               final Consumer<Ad4SseEvent> sink,
                                               final String logCategory) {
        return new Consumer<PiEvent>() {
            @Override
            public void accept(PiEvent event) {
                if (aborted.get()) {
                    return;
                }
                PiEventToAd4Mapper.mapEvent(event).ifPresent(mapped -> {
                    if (aborted.get()) {
                        return;
                    }
                    try {
                        if (Ad4EventName.message_delta.equals(mapped.getName())) {
                            hasMessageDelta.set(true);
                        }
                        emit(sink, mapped);
                    } catch (RuntimeException ex) {
                        aborted.set(true);
                        LoggerUtils.error(log, AgentApplicationService.class, logCategory,
                                ex.getMessage() != null ? ex.getMessage() : "SSE send failed",
                                NameValue.create("runId", runId));
                    }
                });
            }
        };
    }

    private void markRunFailed(String runId) {
        GenerationRun run = generationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        run.markFailed(Instant.now(clock));
        generationRunRepository.update(run);
    }

    private void markRunSettled(String runId, String artifactRef) {
        GenerationRun run = generationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        run.markSettled(artifactRef, Instant.now(clock));
        generationRunRepository.update(run);
    }

    private static Map<String, Object> toRunStarted(String runId, String sessionId, String holdId) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("runId", runId);
        data.put("sessionId", sessionId);
        data.put("holdId", holdId);
        return data;
    }

    private static Map<String, Object> toRunFailed(String reason, boolean emptyRun) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("reason", reason);
        if (emptyRun) {
            data.put("emptyRun", true);
        }
        return data;
    }

    private static Map<String, Object> toArtifactReady(PersistedGenerationArtifact persisted,
                                                       Map<String, Object> projectedView) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("artifactRef", persisted.getArtifactRef());
        data.put("view", projectedView);
        data.putAll(persisted.getReadyExtras());
        return data;
    }

    private static Map<String, Object> toRunSettled(GenerationRunContext context, String artifactRef) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("runId", context.getRunId());
        data.put("holdId", context.getHoldId());
        data.put("artifactRef", artifactRef);
        data.put("amount", 1);
        return data;
    }

    private static void emit(Consumer<Ad4SseEvent> sink, Ad4SseEvent event) {
        sink.accept(event);
    }

    private static void emitRunFailed(Consumer<Ad4SseEvent> sink, String reason, boolean emptyRun) {
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_failed, toRunFailed(reason, emptyRun)));
        } catch (RuntimeException ignored) {
            // 第二次抛错不应抹掉失败收尾尝试
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // ignore
        }
    }
}

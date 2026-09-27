package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.command.StartPicklistRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.dto.PicklistRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.sse.PiEventToAd4Mapper;
import com.xmut.ebus.application.business.computer.ViewProjectContext;
import com.xmut.ebus.application.business.computer.ViewProjectorChain;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.service.PicklistApplicationService;
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
 * 计费选品：可用成果落库后 {@code settle}，再发 {@code artifact_ready}/{@code run_settled}。
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
    static final String MSG_PROMPT_REQUIRED = "请先描述选品需求";

    private final CreditApplicationService creditService;
    private final GenerationRunRepository generationRunRepository;
    private final PiSessionSceneRepository piSessionSceneRepository;
    private final SceneRepository sceneRepository;
    private final SceneCapabilityPackLoader sceneCapabilityPackLoader;
    private final AgentSession agentSession;
    private final PicklistArtifactParser picklistArtifactParser;
    private final PicklistApplicationService picklistApplicationService;
    private final ViewProjectorChain viewProjectorChain;
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
     * 同步解析场景、绑定会话、预占并落 GenerationRun；失败时不预占、不落 Run。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmptyRunContext prepareEmptyRun(StartEmptyRunCommand command) {
        ObjectUtils.requireNonNull(command, "空跑命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");

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

        LoggerUtils.success(log, AgentApplicationService.class, "prepareEmptyRun",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("holdId", holdId),
                NameValue.create("sessionId", sessionId),
                NameValue.create("sceneId", scene.getId()),
                NameValue.create("sceneCode", scene.getSceneCode()));
        return new EmptyRunContext(runId, userId, holdId, sessionId, scene.getSceneCode());
    }

    /**
     * 计费选品：场景绑定 + 预占 + GenerationRun（与空跑同模式，额外校验用户文案）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PicklistRunContext preparePicklistRun(StartPicklistRunCommand command) {
        ObjectUtils.requireNonNull(command, "选品命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");
        String promptText = StringUtils.requireHasText(command.getText(), MSG_PROMPT_REQUIRED);

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

        LoggerUtils.success(log, AgentApplicationService.class, "preparePicklistRun",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("holdId", holdId),
                NameValue.create("sessionId", sessionId),
                NameValue.create("sceneId", scene.getId()),
                NameValue.create("sceneCode", scene.getSceneCode()));
        return new PicklistRunContext(runId, userId, holdId, sessionId, scene.getSceneCode(), promptText.trim());
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
        return creditService.reserveOne(userId);
    }

    /**
     * 流式空跑：{@code run_started} → 按 sceneCode 装包 → Pi 进度映射 → 结束 release + {@code run_failed}。
     * 绝不调用 settle；异常路径同样尝试 release + {@code run_failed}。
     */
    public void streamEmptyRun(EmptyRunContext context, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(context, "空跑上下文不能为空");
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");

        AtomicBoolean hasMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean released = false;
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_started, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            final SceneCapabilityPack pack;
            try {
                pack = sceneCapabilityPackLoader.load(context.getSceneCode());
            } catch (BusinessException ex) {
                boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
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
            if (!pack.hasSkill(SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID)) {
                boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
                released = releaseOk;
                markRunFailed(context.getRunId());
                emitRunFailed(sink, releaseOk
                        ? SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE
                        : RELEASE_FAILED_REASON, true);
                return;
            }

            final Consumer<PiEvent> listener = new Consumer<PiEvent>() {
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
                            LoggerUtils.error(log, AgentApplicationService.class, "streamEmptyRun",
                                    ex.getMessage() != null ? ex.getMessage() : "SSE send failed",
                                    NameValue.create("runId", context.getRunId()));
                        }
                    });
                }
            };

            subscription = agentSession.subscribe(listener);

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text("empty-run")
                    .skillId(SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID)
                    .build());

            if (aborted.get()) {
                boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
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

            boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
            released = releaseOk;
            markRunFailed(context.getRunId());
            emitRunFailed(sink, releaseOk ? EMPTY_RUN_FAIL_REASON : RELEASE_FAILED_REASON, true);
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamEmptyRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (!released) {
                boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
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

    /**
     * 计费选品流：预占已完成 → prompt(skill) → 解析落库 → settle → {@code artifact_ready}/{@code run_settled}。
     * 失败/不合格：release + {@code run_failed}；绝不因 SSE 结束单独 settle。
     */
    public void streamPicklistRun(PicklistRunContext context, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(context, "选品上下文不能为空");
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");

        AtomicBoolean hasMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean holdClosed = false;
        boolean settledOk = false;
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
            if (!pack.hasSkill(SceneCapabilityPackLoader.SKILL_PICKLIST)) {
                holdClosed = finishFailed(context, sink, SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
                return;
            }

            final Consumer<PiEvent> listener = new Consumer<PiEvent>() {
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
                            LoggerUtils.error(log, AgentApplicationService.class, "streamPicklistRun",
                                    ex.getMessage() != null ? ex.getMessage() : "SSE send failed",
                                    NameValue.create("runId", context.getRunId()));
                        }
                    });
                }
            };

            subscription = agentSession.subscribe(listener);

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text(context.getPromptText())
                    .skillId(SceneCapabilityPackLoader.SKILL_PICKLIST)
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

            final PersistPicklistCommand persistCommand;
            try {
                PersistPicklistCommand parsed = picklistArtifactParser.parse(
                        result.getFinalResponse(), context.getUserId(), context.getRunId());
                persistCommand = PersistPicklistCommand.builder()
                        .userId(parsed.getUserId())
                        .username(parsed.getUsername())
                        .runId(parsed.getRunId())
                        .sceneCode(context.getSceneCode())
                        .templateId(parsed.getTemplateId())
                        .disclaimer(parsed.getDisclaimer())
                        .assumptions(parsed.getAssumptions())
                        .items(parsed.getItems())
                        .build();
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : PicklistArtifactParser.MSG_UNUSABLE);
                return;
            }

            PicklistArtifactDTO artifact = picklistApplicationService.persistUsable(persistCommand);

            try {
                creditService.settle(context.getUserId(), context.getHoldId());
            } catch (BusinessException ex) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamPicklistRun",
                        ex.getMessage() != null ? ex.getMessage() : "settle failed",
                        NameValue.create("runId", context.getRunId()),
                        NameValue.create("holdId", context.getHoldId()));
                markRunFailed(context.getRunId());
                emitRunFailed(sink, PICKLIST_SETTLE_FAILED, false);
                holdClosed = true;
                return;
            }

            markRunSettled(context.getRunId(), artifact.getPicklistId());
            settledOk = true;
            holdClosed = true;

            try {
                emit(sink, Ad4SseEvent.of(Ad4EventName.artifact_ready, toArtifactReady(artifact)));
                emit(sink, Ad4SseEvent.of(Ad4EventName.run_settled, toRunSettled(context, artifact.getPicklistId())));
            } catch (RuntimeException emitEx) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamPicklistRun",
                        emitEx.getMessage() != null ? emitEx.getMessage() : "SSE emit after settle failed",
                        NameValue.create("runId", context.getRunId()),
                        NameValue.create("picklistId", artifact.getPicklistId()));
            }

            LoggerUtils.success(log, AgentApplicationService.class, "streamPicklistRun",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("picklistId", artifact.getPicklistId()),
                    NameValue.create("itemCount", artifact.getItems().size()));
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamPicklistRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (settledOk) {
                // 已 settle：禁止 markRunFailed 把 SETTLED 翻成 FAILED；仅记日志
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
    private void logModelUsagePlaceholder(PicklistRunContext context, TurnResult result) {
        Integer responseChars = result != null && result.getFinalResponse() != null
                ? Integer.valueOf(result.getFinalResponse().length())
                : null;
        String status = result != null && result.getStatus() != null
                ? result.getStatus().name()
                : "UNKNOWN";
        LoggerUtils.success(log, AgentApplicationService.class, "modelUsage",
                NameValue.create("runId", context.getRunId()),
                NameValue.create("sessionId", context.getSessionId()),
                NameValue.create("skillId", SceneCapabilityPackLoader.SKILL_PICKLIST),
                NameValue.create("turnStatus", status),
                NameValue.create("promptTokens", null),
                NameValue.create("completionTokens", null),
                NameValue.create("totalTokens", null),
                NameValue.create("estimatedCost", null),
                NameValue.create("responseChars", responseChars));
    }

    private boolean finishFailed(PicklistRunContext context, Consumer<Ad4SseEvent> sink, String reason) {
        boolean releaseOk = releaseHold(context.getUserId(), context.getHoldId(), context.getRunId());
        markRunFailed(context.getRunId());
        emitRunFailed(sink, releaseOk ? reason : PICKLIST_RELEASE_FAILED, false);
        return true;
    }

    /**
     * @return true 仅当 release 成功
     */
    private boolean releaseHold(String userId, String holdId, String runId) {
        try {
            creditService.release(userId, holdId);
            LoggerUtils.success(log, AgentApplicationService.class, "release",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId),
                    NameValue.create("runId", runId));
            return true;
        } catch (BusinessException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占失败",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId));
            return false;
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占异常",
                    NameValue.create("userId", userId),
                    NameValue.create("holdId", holdId));
            return false;
        }
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

    private Map<String, Object> toArtifactReady(PicklistArtifactDTO artifact) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("artifactType", "picklist");
        data.put("artifactRef", artifact.getPicklistId());
        data.put("picklistId", artifact.getPicklistId());
        data.put("runId", artifact.getRunId());
        data.put("templateId", artifact.getTemplateId());
        data.put("disclaimer", artifact.getDisclaimer());
        if (StringUtils.hasText(artifact.getAssumptions())) {
            data.put("assumptions", artifact.getAssumptions());
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PicklistArtifactDTO.PicklistItemDTO item : artifact.getItems()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("title", item.getTitle());
            row.put("priceBand", item.getPriceBand());
            row.put("painPoint", item.getPainPoint());
            row.put("angle", item.getAngle());
            row.put("diff", item.getDiff());
            row.put("niche", item.getNiche());
            row.put("demand", item.getDemand());
            row.put("competition", item.getCompetition());
            row.put("margin", item.getMargin());
            row.put("risk", item.getRisk());
            items.add(row);
        }
        data.put("items", items);
        ViewProjectContext viewCtx = ViewProjectContext.builder()
                .skillBound(true)
                .artifact(artifact)
                .build();
        Optional<Map<String, Object>> projected = viewProjectorChain.project(viewCtx);
        data.put("view", projected.isPresent()
                ? projected.get()
                : java.util.Collections.<String, Object>emptyMap());
        return data;
    }

    private static Map<String, Object> toRunSettled(PicklistRunContext context, String artifactRef) {
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

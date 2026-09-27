package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.sse.PiEventToAd4Mapper;
import com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin;
import com.xmut.ebus.application.business.agent.support.BilledRunInterceptor;
import com.xmut.ebus.application.business.agent.support.BilledRunContext;
import com.xmut.ebus.application.business.agent.support.CreditHoldSupport;
import com.xmut.ebus.application.business.agent.support.GenerationOutputParser;
import com.xmut.ebus.application.business.agent.support.ParsedGenerationOutput;
import com.xmut.ebus.application.business.agent.support.PersistedGenerationArtifact;
import com.xmut.ebus.application.business.agent.support.SkillRunProfile;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.ViewProjectContext;
import com.xmut.ebus.application.business.media.support.ListingMediaMountSupport;
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
import java.util.Collections;
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
 * 非 dry 统一 {@code streamBilledRun}：pre → prompt → parse → view → post → persist → settle。
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
    static final String LISTING_MODEL_FAILED = "上架素材生成失败，请稍后重试";
    public static final String LISTING_SETTLE_FAILED = "上架素材已生成但结算失败，请联系支持";

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
    private final GenerationOutputParser generationOutputParser;
    private final ArtifactPersistPlugin artifactPersistPlugin;
    private final ComputerViewResolver computerViewResolver;
    private final List<BilledRunInterceptor> billedRunInterceptors;
    private final Clock clock;

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
        binding(sessionId, scene);

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

    private void binding(String sessionId, Scene scene) {
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
     * 通用 Generation SSE：dry → run_failed；否则 billed 路径（含无 Skill）。
     */
    public void streamGenerationRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(context, "生成上下文不能为空");
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");
        ObjectUtils.requireNonNull(context.getProfile(), "SkillRunProfile 不能为空");

        if (context.getProfile().isDryRun()) {
            streamDryRun(context, sink);
            return;
        }
        streamBilledRun(context, sink);
    }

    private void streamDryRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
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

            final String runId = context.getRunId();
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
                            emit(sink, mapped);
                        } catch (RuntimeException ex) {
                            aborted.set(true);
                            LoggerUtils.error(log, AgentApplicationService.class, "accept",
                                    ex.getMessage() != null ? ex.getMessage() : "SSE send failed",
                                    NameValue.create("runId", runId));
                        }
                    });
                }
            };

            subscription = agentSession.subscribe(listener);

            agentSession.prompt(PromptRequest.builder()
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

    /**
     * Settle 路径：pre → prompt → parse → view → post → persist → settle → artifact_ready + run_settled。
     * 投影失败不落库、不 settle；{@code persistAs=none} 仍写 chat artifact。
     */
    private void streamBilledRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        AtomicBoolean aborted = new AtomicBoolean(false);

        AutoCloseable subscription = null;
        boolean holdClosed = false;
        boolean settledOk = false;
        SkillRunProfile profile = context.getProfile();
        BilledRunContext runContext = new BilledRunContext(context);
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
            if (profile.isSkillBound() && !pack.hasSkill(profile.getSkillId())) {
                holdClosed = finishFailed(context, sink, SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
                return;
            }

            try {
                for (BilledRunInterceptor interceptor : billedRunInterceptors) {
                    interceptor.before(runContext);
                }
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "生成准备失败");
                return;
            }

            final String runId = context.getRunId();
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
                            emit(sink, mapped);
                        } catch (RuntimeException ex) {
                            aborted.set(true);
                            LoggerUtils.error(log, AgentApplicationService.class, "accept",
                                    ex.getMessage() != null ? ex.getMessage() : "SSE send failed",
                                    NameValue.create("runId", runId));
                        }
                    });
                }
            };

            subscription = agentSession.subscribe(listener);

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text(context.getPromptText())
                    .skillId(profile.getSkillId())
                    .build());

            loggingAgentUsage(context, result);

            if (aborted.get()) {
                holdClosed = finishFailed(context, sink, SSE_SEND_FAILED_RELEASED);
                return;
            }

            if (!TurnResult.Status.OK.equals(result.getStatus())) {
                String reason = StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : (profile.isBilledListing() ? LISTING_MODEL_FAILED : PICKLIST_MODEL_FAILED);
                holdClosed = finishFailed(context, sink, reason);
                return;
            }

            if (StringUtils.hasText(result.getFinalResponse())) {
                Map<String, Object> delta = new LinkedHashMap<>();
                delta.put("text", result.getFinalResponse());
                emit(sink, Ad4SseEvent.of(Ad4EventName.message_delta, delta));
            }

            ParsedGenerationOutput parsed = generationOutputParser.parse(result.getFinalResponse());
            Map<String, Object> projectedView;
            try {
                projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                        .skillBound(profile.isSkillBound())
                        .finalResponse(result.getFinalResponse())
                        .rawView(parsed.getRawView())
                        .artifact(parsed.getBusinessPayload())
                        .build());
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ComputerViewResolver.MSG_VIEW_UNAVAILABLE);
                return;
            }

            runContext.setProjectedView(projectedView);
            runContext.setBusinessPayload(parsed.getBusinessPayload());
            try {
                for (BilledRunInterceptor interceptor : billedRunInterceptors) {
                    interceptor.after(runContext);
                }
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage())
                                ? ex.getMessage()
                                : ListingMediaMountSupport.MSG_MEDIA_BUSY);
                return;
            }
            projectedView = runContext.getProjectedView();
            Map<String, Object> businessPayload = runContext.getBusinessPayload();

            final PersistedGenerationArtifact persisted;
            try {
                persisted = artifactPersistPlugin.persist(
                        context.getUserId(), context.getRunId(), context.getSceneCode(),
                        profile.getPersistAs(), projectedView, businessPayload);
            } catch (BusinessException ex) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "成果落库失败");
                return;
            }

            // Known edge (no redesign): persist may succeed before settle; if settle throws,
            // ebus_artifact row can exist while run stays unsettled (common on no-skill chat path).
            // CreditHoldSupport settle failure path releases the hold; client sees run_failed, not run_settled.
            try {
                creditHoldSupport.settle(context.getUserId(), context.getHoldId());
            } catch (BusinessException ex) {
                LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                        ex.getMessage() != null ? ex.getMessage() : "settle failed",
                        NameValue.create("runId", context.getRunId()),
                        NameValue.create("holdId", context.getHoldId()));
                markRunFailed(context.getRunId());
                emitRunFailed(sink,
                        profile.isBilledListing() ? LISTING_SETTLE_FAILED : PICKLIST_SETTLE_FAILED, false);
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
                    NameValue.create("skillId", profile.getSkillId()),
                    NameValue.create("skillBound", profile.isSkillBound()));
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (settledOk) {
                return;
            }
            String fallback = profile.isBilledListing() ? LISTING_MODEL_FAILED : PICKLIST_MODEL_FAILED;
            if (!holdClosed) {
                holdClosed = finishFailed(context, sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : fallback);
            } else {
                markRunFailed(context.getRunId());
                emitRunFailed(sink,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : fallback, false);
            }
        } finally {
            closeQuietly(subscription);
        }
    }

    /**
     * NFR2：单次 Run 用量/成本可观测（近端占位字段；真实 token 待 TurnResult 贯通后替换）。
     */
    private void loggingAgentUsage(GenerationRunContext context, TurnResult result) {
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
        if (StringUtils.hasText(persisted.getArtifactRef())) {
            data.put("artifactRef", persisted.getArtifactRef());
        }
        data.put("view", projectedView);
        data.putAll(persisted.getReadyExtras());
        return data;
    }

    private static Map<String, Object> toRunSettled(GenerationRunContext context, String artifactRef) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("runId", context.getRunId());
        data.put("holdId", context.getHoldId());
        if (StringUtils.hasText(artifactRef)) {
            data.put("artifactRef", artifactRef);
        }
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

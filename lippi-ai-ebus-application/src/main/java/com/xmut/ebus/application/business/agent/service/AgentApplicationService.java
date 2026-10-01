package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.ResumeGenerationRunCommand;
import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.sse.PiEventToAd4Mapper;
import com.xmut.ebus.application.business.agent.support.ArtifactPersistPlugin;
import com.xmut.ebus.application.business.agent.support.BilledRunContext;
import com.xmut.ebus.application.business.agent.support.BilledRunInterceptor;
import com.xmut.ebus.application.business.agent.support.BilledRunListener;
import com.xmut.ebus.application.business.agent.support.BilledSuspendedHandler;
import com.xmut.ebus.application.business.agent.support.CreditHoldSupport;
import com.xmut.ebus.application.business.agent.support.GenerationOutputParser;
import com.xmut.ebus.application.business.agent.support.ListingHitlOptions;
import com.xmut.ebus.application.business.agent.support.ParsedGenerationOutput;
import com.xmut.ebus.application.business.agent.support.PersistedGenerationArtifact;
import com.xmut.ebus.application.business.agent.support.SkuHitlInterceptor;
import com.xmut.ebus.application.business.agent.support.SkillRunProfile;
import com.xmut.ebus.application.business.agent.workspace.RunWorkspaceService;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.ViewProjectContext;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.application.business.session.query.SessionQueryService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.ObjectUtils;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.model.SessionSceneBinding;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.domain.business.agent.repository.PiSessionSceneRepository;
import com.xmut.ebus.domain.business.scene.constant.SceneStatus;
import com.xmut.ebus.domain.business.scene.model.Scene;
import com.xmut.ebus.domain.business.scene.repository.SceneRepository;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.session.AgentSession;
import com.xmut.lims.pi.agent.session.PromptRequest;
import com.xmut.lims.pi.agent.session.TurnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Agent 运行时应用服务：预占积分、驱动 {@link AgentSession}、经 AD-4 SSE 回传事件并结算。
 *
 * <p>入口分两条：首跑 {@link #prepareGenerationRun} + {@link #streamGenerationRun}；
 * 续跑 {@link #prepareResumeGenerationRun} + {@link #resumeBilledRun}。
 * 业务差异经 Interceptor / Listener / SuspendedHandler 扩展，勿在计费主路径写 profile 分支。
 * 空跑只 reserve+release，永不 settle；计费须绑定 AVAILABLE 场景（AD-15）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    static final String EMPTY_RUN_FAIL_REASON = "空跑无可用成果，预占已释放";
    static final String RELEASE_FAILED_REASON = "空跑结束但预占释放失败";
    static final String SSE_SEND_FAILED_RELEASED = "SSE 下发失败，预占已释放";
    static final String SSE_SEND_FAILED_RELEASE_FAILED = "SSE 下发失败且预占释放失败";
    /** 计费管线通用：模型回合失败。 */
    static final String MODEL_FAILED = "生成失败，请稍后重试";
    /** 计费管线通用：成果已落库但 settle 失败。 */
    static final String SETTLE_FAILED = "成果已生成但结算失败，请联系支持";

    public static final String MSG_RESUME_NOT_AWAITING = "当前回合未在等待确认，无法续跑";
    public static final String MSG_RESUME_TOOL_CALL_REQUIRED = "请提供 toolCallId";

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
    private final List<BilledRunListener> billedRunListeners;
    private final List<BilledSuspendedHandler> billedSuspendedHandlers;
    private final Checkpointer checkpointer;
    private final Clock clock;
    private final RunWorkspaceService runWorkspaceService;

    /**
     * 首跑同步门闩：场景绑定 + 预占 + 落 GenerationRun；随后由 Controller 开 SSE 调 {@link #streamGenerationRun}。
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
        binding(sessionId, scene, userId);

        String holdId = reserveOne(userId);
        Instant now = Instant.now(clock);
        String runId = UUID.randomUUID().toString();
        GenerationRun run = GenerationRun.start(
                runId, userId, holdId, sessionId, scene.getId(), scene.getSceneCode(),
                profile.getSkillId(), now);
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

    private void binding(String sessionId, Scene scene, String userId) {
        Optional<SessionSceneBinding> existing = piSessionSceneRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            SessionSceneBinding bound = existing.get();
            if (StringUtils.hasText(bound.getUserId()) && !userId.equals(bound.getUserId().trim())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, SessionQueryService.MSG_UNAVAILABLE);
            }
            if (bound.hasScene()
                    && (!scene.getId().equals(bound.getSceneId())
                    || !scene.getSceneCode().equals(bound.getSceneCode()))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_SESSION_SCENE_MISMATCH);
            }
        }
        try {
            piSessionSceneRepository.ensureBound(sessionId, scene.getId(), scene.getSceneCode(), userId);
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
            emit(sink, Ad4SseEvent.of(Ad4EventName.RUN_STARTED, toRunStarted(context.getRunId(),
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

            Path runDir = ensureRunWorkspace(context.getSessionId(), context.getRunId());
            agentSession.prompt(promptRequest(context, profile, runDir));

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
     * 执行一次计费首跑，并向 {@code sink} 推送 AD-4 SSE。
     *
     * <p>与 {@link #resumeBilledRun} 步骤对齐：前置钩子、订阅 PiEvent、调用
     * {@link AgentSession#prompt}，再按 {@code SUSPENDED} / 失败 / {@code OK}
     * 分流（解析终稿、投影 Computer View、后置钩子、落库结算）。
     * 业务特化经 Interceptor / Listener / SuspendedHandler，本方法不做 profile 分支。
     *
     * @param context 已预占并绑定场景的运行上下文
     * @param sink    SSE 事件消费者
     */
    private void streamBilledRun(GenerationRunContext context, Consumer<Ad4SseEvent> sink) {
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        SkillRunProfile profile = context.getProfile();
        BilledRunContext runContext = new BilledRunContext(context);
        try {
            // 1. BilledRun 前置处理
            beforeBilledRun(runContext);

            // 2. 发布 RUN_STARTED 事件
            emit(sink, Ad4SseEvent.of(Ad4EventName.RUN_STARTED, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            // 3. 校验场景能力包
            SceneCapabilityPack pack = sceneCapabilityPackLoader.load(context.getSceneCode());
            if (profile.isSkillBound() && !pack.hasSkill(profile.getSkillId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
            }

            // 4. 订阅 PiEvent 并转发为 AD-4 SSE
            final String runId = context.getRunId();
            subscription = agentSession.subscribe(new Consumer<PiEvent>() {
                @Override
                public void accept(PiEvent event) {
                    if (aborted.get()) {
                        return;
                    }
                    PiEventToAd4Mapper.mapEvent(event).ifPresent(mapped -> {
                        if (aborted.get()) {
                            return;
                        }
                        notifyMappedEvent(runContext, mapped);
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
            });

            // 5. 调用 AgentSession.prompt
            Path runDir = ensureRunWorkspace(context.getSessionId(), context.getRunId());
            TurnResult result = agentSession.prompt(promptRequest(context, profile, runDir));

            loggingAgentUsage(context, result);

            if (aborted.get()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, SSE_SEND_FAILED_RELEASED);
            }

            // 6a. SUSPENDED：挂上 Turn 结果 → handler 业务副作用 → 结算当前 hold
            if (TurnResult.Status.SUSPENDED.equals(result.getStatus())) {
                runContext.bindTurnResult(result);
                for (BilledSuspendedHandler handler : billedSuspendedHandlers) {
                    handler.onSuspended(runContext, sink);
                }
                settleOnSuspended(runContext, sink);
                return;
            }

            // 6b. 失败收尾
            if (!TurnResult.Status.OK.equals(result.getStatus())) {
                String reason = StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : MODEL_FAILED;
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, reason);
            }

            // 7. 发布终稿 MESSAGE_DELTA，解析并投影 Computer View
            String finalResponse = result.getFinalResponse();
            if (StringUtils.hasText(finalResponse)) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", finalResponse);
                emit(sink, Ad4SseEvent.of(Ad4EventName.MESSAGE_DELTA, delta));
            }

            ParsedGenerationOutput parsed = parseFinalOutput(finalResponse, runDir);
            Map<String, Object> projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                    .skillBound(profile.isSkillBound())
                    .finalResponse(finalResponse)
                    .rawView(parsed.getRawView())
                    .artifact(parsed.getBusinessPayload())
                    .build());

            runContext.setProjectedView(projectedView);
            runContext.setBusinessPayload(parsed.getBusinessPayload());

            // 8. BilledRun 后置处理
            afterBilledRun(runContext);

            // 9. BilledRun 落库并结算
            settleBilledRun(context, sink, runContext.getPersistAs(), runContext.getProjectedView(), runContext.getBusinessPayload());
        } catch (BusinessException ex) {
            if (isRunSettled(context.getRunId())) {
                return;
            }
            releaseOpenHolds(context);
            markRunFailed(context.getRunId());
            emitRunFailed(sink, ex.getMessage(), false);
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamGenerationRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (isRunSettled(context.getRunId())) {
                return;
            }
            releaseOpenHolds(context);
            markRunFailed(context.getRunId());
            emitRunFailed(sink, ex.getMessage(), false);
        } finally {
            closeQuietly(subscription);
        }
    }

    /**
     * Resume 同步门闩：归属 / RUNNING / Checkpoint 存在（挂起真源）；非法时抛业务错（勿开 SSE）。
     */
    public GenerationRunContext prepareResumeGenerationRun(ResumeGenerationRunCommand command) {
        ObjectUtils.requireNonNull(command, "续跑命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");
        String runId = StringUtils.requireHasText(command.getRunId(), "runId 不能为空");
        StringUtils.requireHasText(command.getToolCallId(), MSG_RESUME_TOOL_CALL_REQUIRED);
        resolveResumeOptionId(command.getOptionId(), command.getFreeText());

        GenerationRun run = generationRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, MSG_RESUME_NOT_AWAITING));
        if (!userId.equals(run.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_RESUME_NOT_AWAITING);
        }
        if (run.getStatus() != GenerationRunStatus.RUNNING) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_RESUME_NOT_AWAITING);
        }
        if (!checkpointer.loadLatest(runId).isPresent()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_RESUME_NOT_AWAITING);
        }
        return rebuildContextFromRun(run);
    }

    private GenerationRunContext rebuildContextFromRun(GenerationRun run) {
        SkillRunProfile profile = SkillRunProfile.resolve(run.getSkillId(), false);
        GenerationRunContext context = new GenerationRunContext(
                run.getId(),
                run.getUserId(),
                run.getHoldId(),
                run.getSessionId(),
                run.getSceneCode(),
                "",
                profile);
        if (StringUtils.hasText(run.getArtifactRef())) {
            context.setArtifactRef(run.getArtifactRef());
        }
        if (StringUtils.hasText(run.getExecHoldId())) {
            context.bindExecHold(run.getExecHoldId());
            if (StringUtils.hasText(run.getHoldId())) {
                context.setHoldId(run.getHoldId());
            }
        }
        return context;
    }

    /**
     * 执行 ask_human 续跑，并向 {@code sink} 推送 AD-4 SSE。
     *
     * <p>与 {@link #streamBilledRun} 步骤对齐，将人工选项作为 user 消息经
     * {@link AgentSession#resume} 续跑（tool 回执已在 interrupt 时写入）。
     * 挂起真源为 Checkpoint；Listing 定制见 {@link SkuHitlInterceptor}。
     *
     * @param command 续跑命令（含 runId、toolCallId、选项或自由文本）
     * @param sink    SSE 事件消费者，不可为空
     */
    public void resumeBilledRun(ResumeGenerationRunCommand command, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");

        // 1. 校验续跑门闩（归属 / RUNNING / Checkpoint）
        GenerationRunContext context = prepareResumeGenerationRun(command);
        SkillRunProfile profile = context.getProfile();
        String runId = context.getRunId();
        String toolCallId = command.getToolCallId();
        String optionId = resolveResumeOptionId(command.getOptionId(), command.getFreeText());
        String humanInput = toAskHumanInput(optionId, command.getFreeText());

        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        BilledRunContext runContext = new BilledRunContext(context);
        runContext.setResumeOptionId(optionId);
        runContext.setAfterResume(true);

        try {
            // 2. BilledRun 前置处理
            beforeBilledRun(runContext);

            // 3. 发布 RUN_STARTED 事件
            emit(sink, Ad4SseEvent.of(Ad4EventName.RUN_STARTED, toRunStarted(context.getRunId(),
                    context.getSessionId(), context.getHoldId())));

            // 4. 订阅 PiEvent 并转发为 AD-4 SSE
            subscription = agentSession.subscribe(new Consumer<PiEvent>() {
                @Override
                public void accept(PiEvent event) {
                    if (aborted.get()) {
                        return;
                    }
                    PiEventToAd4Mapper.mapEvent(event).ifPresent(mapped -> {
                        if (aborted.get()) {
                            return;
                        }
                        notifyMappedEvent(runContext, mapped);
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
            });

            // 5. 确保同 run 工作区仍在，再调用 AgentSession.resume
            Path runDir = ensureRunWorkspace(context.getSessionId(), context.getRunId());
            TurnResult result = agentSession.resume(ResumeRequest.builder()
                    .runId(runId)
                    .sessionId(context.getSessionId())
                    .toolCallId(toolCallId)
                    .humanInput(humanInput)
                    .confirmId(command.getConfirmId())
                    .build());

            loggingAgentUsage(context, result);

            if (aborted.get()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, SSE_SEND_FAILED_RELEASED);
            }

            // 6a. SUSPENDED：挂上 Turn 结果 → handler 业务副作用 → 释放不该留下的 hold
            if (TurnResult.Status.SUSPENDED.equals(result.getStatus())) {
                runContext.bindTurnResult(result);
                for (BilledSuspendedHandler handler : billedSuspendedHandlers) {
                    handler.onSuspended(runContext, sink);
                }
                releaseOnSuspended(context);
                return;
            }

            // 6b. 非 OK：按失败收尾
            if (!TurnResult.Status.OK.equals(result.getStatus())) {
                String reason = StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : MODEL_FAILED;
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, reason);
            }

            // 7. OK：发布终稿 MESSAGE_DELTA，解析并投影 Computer View
            String finalResponse = result.getFinalResponse();
            if (StringUtils.hasText(finalResponse)) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", finalResponse);
                emit(sink, Ad4SseEvent.of(Ad4EventName.MESSAGE_DELTA, delta));
            }
            ParsedGenerationOutput parsed = parseFinalOutput(finalResponse, runDir);
            Map<String, Object> projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                    .skillBound(profile.isSkillBound())
                    .finalResponse(finalResponse)
                    .rawView(parsed.getRawView())
                    .artifact(parsed.getBusinessPayload())
                    .build());
            runContext.setProjectedView(projectedView);
            runContext.setBusinessPayload(parsed.getBusinessPayload());

            // 8. BilledRun 后置处理，落库并结算
            afterBilledRun(runContext);

            settleBilledRun(context, sink, runContext.getPersistAs(),
                    runContext.getProjectedView(), runContext.getBusinessPayload());
        } catch (BusinessException ex) {
            if (isRunSettled(context.getRunId())) {
                return;
            }

            releaseOpenHolds(context);
            markRunFailed(context.getRunId());
            emitRunFailed(sink, ex.getMessage(), false);
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "resumeBilledRun",
                    ex.getMessage() != null ? ex.getMessage() : "resume failed",
                    NameValue.create("runId", runId));
            if (isRunSettled(context.getRunId())) {
                return;
            }

            releaseOpenHolds(context);
            markRunFailed(context.getRunId());
            emitRunFailed(sink, ex.getMessage(), false);
        } finally {
            closeQuietly(subscription);
        }
    }

    private void notifyMappedEvent(BilledRunContext runContext, Ad4SseEvent mapped) {
        if (mapped.getName() == Ad4EventName.HUMAN_INPUT_REQUIRED) {
            Object callId = mapped.getData().get("toolCallId");
            if (callId != null && StringUtils.hasText(String.valueOf(callId))) {
                runContext.setPendingToolCallId(String.valueOf(callId).trim());
            }
        }
        for (BilledRunListener listener : billedRunListeners) {
            listener.onEvent(runContext, mapped);
        }
    }

    /** {@link BilledRunInterceptor#onBefore}；异常抛给调用方。 */
    private void beforeBilledRun(BilledRunContext runContext) {
        for (BilledRunInterceptor interceptor : billedRunInterceptors) {
            interceptor.onBefore(runContext);
        }
    }

    /** {@link BilledRunInterceptor#onAfter}；异常抛给调用方。 */
    private void afterBilledRun(BilledRunContext runContext) {
        for (BilledRunInterceptor interceptor : billedRunInterceptors) {
            interceptor.onAfter(runContext);
        }
    }

    /** stream 挂起收尾：handler 已落成果则 settle 当前 hold。 */
    private void settleOnSuspended(BilledRunContext runContext, Consumer<Ad4SseEvent> sink) {
        if (!runContext.isPendingSettleOnSuspend()) {
            return;
        }
        GenerationRunContext context = runContext.getRun();
        String holdId = context.getHoldId();
        if (!StringUtils.hasText(holdId)) {
            return;
        }
        try {
            creditHoldSupport.settle(context.getUserId(), holdId);
        } catch (BusinessException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "settleOnSuspended",
                    ex.getMessage() != null ? ex.getMessage() : "settle failed",
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("holdId", holdId));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, SETTLE_FAILED);
        }
        String artifactRef = context.getArtifactRef();
        context.markSettledOnSuspended(artifactRef);
        GenerationRun run = generationRunRepository.findById(context.getRunId()).orElse(null);
        if (run != null) {
            run.markSettledOnSuspended(artifactRef, Instant.now(clock));
            generationRunRepository.update(run);
        }
        runContext.setPendingSettleOnSuspend(false);
    }

    /** resume 再挂起收尾：释放不应留下的 exec hold。 */
    private void releaseOnSuspended(GenerationRunContext context) {
        if (!StringUtils.hasText(context.getExecHoldId())) {
            return;
        }
        creditHoldSupport.release(context.getUserId(), context.getExecHoldId(), context.getRunId());
        context.clearExecHold();
        GenerationRun run = generationRunRepository.findById(context.getRunId()).orElse(null);
        if (run != null) {
            run.clearExecHold(Instant.now(clock));
            generationRunRepository.update(run);
        }
    }

    /** persist → settle → artifact_ready + run_settled；失败在方法内收尾，不向外抛。 */
    private void settleBilledRun(GenerationRunContext context,
                                 Consumer<Ad4SseEvent> sink,
                                 String persistAs,
                                 Map<String, Object> projectedView,
                                 Map<String, Object> businessPayload) {
        SkillRunProfile profile = context.getProfile();
        final PersistedGenerationArtifact persisted;
        try {
            persisted = artifactPersistPlugin.persist(
                    context.getUserId(), context.getRunId(), context.getSceneCode(),
                    persistAs, projectedView, businessPayload);
        } catch (BusinessException ex) {
            releaseOpenHolds(context);
            markRunFailed(context.getRunId());
            emitRunFailed(sink, ex.getMessage(), false);
            return;
        }

        String settleHoldId = context.getHoldId();
        try {
            creditHoldSupport.settle(context.getUserId(), settleHoldId);
        } catch (BusinessException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "persistAndSettleBilledRun",
                    ex.getMessage() != null ? ex.getMessage() : "settle failed",
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("holdId", settleHoldId));
            markRunFailed(context.getRunId());
            emitRunFailed(sink, SETTLE_FAILED, false);
            return;
        }

        markRunSettled(context.getRunId(), persisted.getArtifactRef());
        context.setArtifactRef(persisted.getArtifactRef());
        if (StringUtils.hasText(context.getExecHoldId())) {
            context.clearExecHold();
            GenerationRun run = generationRunRepository.findById(context.getRunId()).orElse(null);
            if (run != null) {
                run.clearExecHold(Instant.now(clock));
                generationRunRepository.update(run);
            }
        }

        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.ARTIFACT_READY,
                    toArtifactReady(persisted, projectedView)));
            emit(sink, Ad4SseEvent.of(Ad4EventName.RUN_SETTLED,
                    toRunSettled(context, settleHoldId, persisted.getArtifactRef())));
        } catch (RuntimeException emitEx) {
            LoggerUtils.error(log, AgentApplicationService.class, "persistAndSettleBilledRun",
                    emitEx.getMessage() != null ? emitEx.getMessage() : "SSE emit after settle failed",
                    NameValue.create("runId", context.getRunId()),
                    NameValue.create("artifactRef", persisted.getArtifactRef()));
        }

        runWorkspaceService.deleteRunDirQuietly(context.getSessionId(), context.getRunId());

        LoggerUtils.success(log, AgentApplicationService.class, "streamGenerationRun",
                NameValue.create("userId", context.getUserId()),
                NameValue.create("runId", context.getRunId()),
                NameValue.create("artifactRef", persisted.getArtifactRef()),
                NameValue.create("skillId", profile.getSkillId()),
                NameValue.create("skillBound", profile.isSkillBound()));
    }

    private Path ensureRunWorkspace(String sessionId, String runId) {
        try {
            return runWorkspaceService.ensureRunDir(sessionId, runId);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "工作区创建失败");
        }
    }

    private static PromptRequest promptRequest(GenerationRunContext context, SkillRunProfile profile, Path runDir) {
        return PromptRequest.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .text(context.getPromptText())
                .skillId(profile.getSkillId())
                .workspaceRoot(runDir.toAbsolutePath().toString())
                .build();
    }

    private ParsedGenerationOutput parseFinalOutput(String finalResponse, Path runDir) {
        try {
            return generationOutputParser.parse(finalResponse, runDir);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "成果文件无效");
        }
    }

    private static String resolveResumeOptionId(String optionId, String freeText) {
        if (StringUtils.hasText(optionId)) {
            String id = optionId.trim();
            if (ListingHitlOptions.CONFIRM_EXECUTE.equals(id) || ListingHitlOptions.SUPPLEMENT.equals(id)) {
                return id;
            }
            throw new BusinessException(ErrorCode.PARAM_INVALID, ListingHitlOptions.MSG_OPTION_REQUIRED);
        }
        if (StringUtils.hasText(freeText)) {
            return ListingHitlOptions.SUPPLEMENT;
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, ListingHitlOptions.MSG_OPTION_REQUIRED);
    }

    /** 对齐 {@code toRunStarted}：把选项/自由文本压成 resume 的 humanInput。 */
    private static String toAskHumanInput(String optionId, String freeText) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"optionId\":\"").append(jsonEscape(optionId)).append('"');
        if (StringUtils.hasText(freeText)) {
            sb.append(",\"freeText\":\"").append(jsonEscape(freeText.trim())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isRunSettled(String runId) {
        GenerationRun run = generationRunRepository.findById(runId).orElse(null);
        return run != null && run.getStatus() == GenerationRunStatus.SETTLED;
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

    /** 仅释放当前未关闭的活跃预占。 */
    private void releaseOpenHolds(GenerationRunContext context) {
        if (StringUtils.hasText(context.getHoldId())) {
            creditHoldSupport.release(context.getUserId(), context.getHoldId(), context.getRunId());
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

    private static Map<String, Object> toRunSettled(GenerationRunContext context, String holdId, String artifactRef) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("runId", context.getRunId());
        if (StringUtils.hasText(holdId)) {
            data.put("holdId", holdId);
        }
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
            emit(sink, Ad4SseEvent.of(Ad4EventName.RUN_FAILED, toRunFailed(reason, emptyRun)));
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

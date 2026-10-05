package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.agent.dto.GenerationRunContext;
import com.xmut.forma.application.business.agent.sse.SseEventName;
import com.xmut.forma.application.business.agent.sse.SseEvent;
import com.xmut.forma.application.business.computer.ComputerViewResolver;
import com.xmut.forma.application.business.computer.ViewProjectContext;
import com.xmut.forma.application.business.agent.workspace.RunWorkspaceService;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.common.output.TurnAttachmentProvider;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.agent.model.GenerationRun;
import com.xmut.forma.domain.business.agent.repository.GenerationRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Listing HITL：确认再预占（onBefore）、确认后 persistAs（onAfter）、
 * 策划文本捕获（Listener）、挂起时 persist/echo 策划（SuspendedHandler）。
 * 计费收尾在 {@code settleOnSuspended} / {@code releaseOnSuspended}。
 */
@Slf4j
@Component
public class SkuHitlInterceptor
        implements BilledRunInterceptor, BilledRunListener, BilledSuspendedHandler {

    static final String MSG_LISTING_MODEL_FAILED = "上架素材生成失败，请稍后重试";

    private final CreditHoldSupport creditHoldSupport;
    private final OutputParser outputParser;
    private final ArtifactPersistPlugin artifactPersistPlugin;
    private final ComputerViewResolver computerViewResolver;
    private final GenerationRunRepository generationRunRepository;
    private final Clock clock;
    private final RunWorkspaceService runWorkspaceService;
    private final TurnAttachmentProvider turnAttachmentProvider;

    public SkuHitlInterceptor(CreditHoldSupport creditHoldSupport,
                              OutputParser outputParser,
                              ArtifactPersistPlugin artifactPersistPlugin,
                              ComputerViewResolver computerViewResolver,
                              GenerationRunRepository generationRunRepository,
                              Clock clock,
                              RunWorkspaceService runWorkspaceService,
                              TurnAttachmentProvider turnAttachmentProvider) {
        this.creditHoldSupport = creditHoldSupport;
        this.outputParser = outputParser;
        this.artifactPersistPlugin = artifactPersistPlugin;
        this.computerViewResolver = computerViewResolver;
        this.generationRunRepository = generationRunRepository;
        this.clock = clock;
        this.runWorkspaceService = runWorkspaceService;
        this.turnAttachmentProvider = turnAttachmentProvider;
    }

    @Override
    public void onBefore(BilledRunContext ctx) {
        if (!ctx.getProfile().isBilledSku()) {
            return;
        }
        if (!ListingHitlOptions.CONFIRM_EXECUTE.equals(ctx.getResumeOptionId())) {
            return;
        }
        String execHoldId = creditHoldSupport.reserveOne(ctx.getRun().getUserId());
        ctx.getRun().bindExecHold(execHoldId);
        GenerationRun run = generationRunRepository.findById(ctx.getRun().getRunId()).orElse(null);
        if (run != null) {
            run.bindExecHold(execHoldId, Instant.now(clock));
            generationRunRepository.update(run);
        }
    }

    @Override
    public void onAfter(BilledRunContext ctx) {
        if (!ctx.getProfile().isBilledSku()) {
            return;
        }
        if (!StringUtils.hasText(ctx.getResumeOptionId())) {
            return;
        }
        if (!ListingHitlOptions.CONFIRM_EXECUTE.equals(ctx.getResumeOptionId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_LISTING_MODEL_FAILED);
        }
        ctx.setPersistAs(SkillRunProfile.PERSIST_SKU);
    }

    @Override
    public void onEvent(BilledRunContext ctx, SseEvent event) {
        if (!ctx.getProfile().isBilledSku()) {
            return;
        }
        if (event == null || event.getName() != SseEventName.MESSAGE_DELTA) {
            return;
        }
        Object text = event.getData().get("text");
        if (text == null) {
            return;
        }
        // 流式 delta 是碎片；累加后再解析，避免「先策划再 ask_human」拼不成完整 JSON。
        ctx.appendAssistantDelta(String.valueOf(text));
        captureSkuPlanCandidate(ctx.getAssistantTextBuffer(), ctx);
    }

    @Override
    public boolean onSuspended(BilledRunContext ctx, Consumer<SseEvent> sink) {
        if (!ctx.getProfile().isBilledSku()) {
            return false;
        }
        seedPlanCandidateFromTurn(ctx);
        persistOrEchoSkuPlan(ctx, sink);
        return true;
    }

    /**
     * 策划正文在盘上。缓冲和终稿只用来回显，不再从 assistant 消息里找指针。
     */
    private void seedPlanCandidateFromTurn(BilledRunContext ctx) {
        if (StringUtils.hasText(ctx.getAssistantTextCandidate())) {
            return;
        }
        if (captureSkuPlanCandidate(ctx.getAssistantTextBuffer(), ctx)) {
            return;
        }
        captureSkuPlanCandidate(ctx.getTurnFinalResponse(), ctx);
    }

    private void persistOrEchoSkuPlan(BilledRunContext billedCtx, Consumer<SseEvent> sink) {
        GenerationRunContext context = billedCtx.getRun();
        String toolCallId = billedCtx.getPendingToolCallId();
        ParsedGenerationOutput parsed = parsePlanOutput(billedCtx);
        if (parsed.getRawView() == null
                || !ArtifactPersistPlugin.isUsableSkuPlanPayload(parsed.getBusinessPayload())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ArtifactPersistPlugin.MSG_LISTING_PLAN_UNUSABLE);
        }
        String planText = billedCtx.getAssistantTextCandidate();
        if (!StringUtils.hasText(planText)) {
            planText = billedCtx.getTurnFinalResponse();
        }
        if (!StringUtils.hasText(planText)) {
            planText = "";
        }

        Map<String, Object> projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
                .skillBound(true)
                .finalResponse(planText)
                .rawView(parsed.getRawView())
                .artifact(parsed.getBusinessPayload())
                .build());

        // 同 Run 覆盖写策划；仅首次挂起 settle 策划 hold（须在 setArtifactRef 之前判断）。
        boolean firstPlanSettle = !context.isSettledOnSuspended();
        PersistedGenerationArtifact persisted = artifactPersistPlugin.persist(
                context.getUserId(), context.getRunId(), context.getSceneCode(),
                SkillRunProfile.PERSIST_LISTING_PLAN, projectedView, parsed.getBusinessPayload());
        context.setArtifactRef(persisted.getArtifactRef());
        if (firstPlanSettle) {
            billedCtx.setPendingSettleOnSuspend(true);
        }

        try {
            sink.accept(SseEvent.of(SseEventName.ARTIFACT_READY,
                    toArtifactReady(persisted, projectedView)));
        } catch (RuntimeException emitEx) {
            LoggerUtils.error(log, SkuHitlInterceptor.class, "onSuspended",
                    emitEx.getMessage() != null ? emitEx.getMessage() : "artifact_ready emit failed",
                    NameValue.create("runId", context.getRunId()));
        }

        if (StringUtils.hasText(toolCallId)) {
            context.setPendingToolCallId(toolCallId);
        }
        LoggerUtils.success(log, SkuHitlInterceptor.class, "onSuspended",
                NameValue.create("runId", context.getRunId()),
                NameValue.create("settledOnSuspended", Boolean.valueOf(context.isSettledOnSuspended())),
                NameValue.create("artifactRef", context.getArtifactRef()),
                NameValue.create("pendingSettle", Boolean.valueOf(billedCtx.isPendingSettleOnSuspend())));
    }

    /** @return true when usable plan candidate was set */
    private boolean captureSkuPlanCandidate(String text, BilledRunContext ctx) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        ParsedGenerationOutput parsed;
        try {
            parsed = parsePlanOutput(ctx);
        } catch (BusinessException ex) {
            return false;
        }
        if (parsed.getRawView() == null || parsed.getRawView().isEmpty()) {
            return false;
        }
        if (!ArtifactPersistPlugin.isUsableSkuPlanPayload(parsed.getBusinessPayload())) {
            return false;
        }
        ctx.setAssistantTextCandidate(text.trim());
        return true;
    }

    /**
     * 策划 settle 钉 plan 槽（resumeOptionId 为空）。
     */
    private ParsedGenerationOutput parsePlanOutput(BilledRunContext ctx) {
        String echo = ctx.getAssistantTextCandidate();
        if (!StringUtils.hasText(echo)) {
            echo = ctx.getTurnFinalResponse();
        }
        TurnAttachment att = turnAttachmentProvider.of(ctx.getProfile().getSkillId(), null);
        OutputParseContext parseCtx = OutputParseContext.builder()
                .skillId(ctx.getProfile().getSkillId())
                .sceneCode(ctx.getRun().getSceneCode())
                .resumeOptionId(null)
                .attachment(att)
                .finalResponse(echo)
                .workspaceRoot(runDir(ctx))
                .build();
        if (!outputParser.appliesTo(parseCtx)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ArtifactPersistPlugin.MSG_LISTING_PLAN_UNUSABLE);
        }
        try {
            return outputParser.parse(parseCtx);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    StringUtils.hasText(ex.getMessage())
                            ? ex.getMessage()
                            : ArtifactPersistPlugin.MSG_LISTING_PLAN_UNUSABLE);
        }
    }

    private Path runDir(BilledRunContext ctx) {
        GenerationRunContext run = ctx.getRun();
        return runWorkspaceService.runDir(run.getSessionId(), run.getRunId());
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
}

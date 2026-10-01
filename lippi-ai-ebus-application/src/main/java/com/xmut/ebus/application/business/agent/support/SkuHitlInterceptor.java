package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.computer.ComputerViewResolver;
import com.xmut.ebus.application.business.computer.ViewProjectContext;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.application.business.agent.workspace.RunWorkspaceService;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.xmut.lims.pi.ai.message.Message;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final GenerationOutputParser generationOutputParser;
    private final ArtifactPersistPlugin artifactPersistPlugin;
    private final ComputerViewResolver computerViewResolver;
    private final GenerationRunRepository generationRunRepository;
    private final Clock clock;
    private final RunWorkspaceService runWorkspaceService;

    public SkuHitlInterceptor(CreditHoldSupport creditHoldSupport,
                              GenerationOutputParser generationOutputParser,
                              ArtifactPersistPlugin artifactPersistPlugin,
                              ComputerViewResolver computerViewResolver,
                              GenerationRunRepository generationRunRepository,
                              Clock clock,
                              RunWorkspaceService runWorkspaceService) {
        this.creditHoldSupport = creditHoldSupport;
        this.generationOutputParser = generationOutputParser;
        this.artifactPersistPlugin = artifactPersistPlugin;
        this.computerViewResolver = computerViewResolver;
        this.generationRunRepository = generationRunRepository;
        this.clock = clock;
        this.runWorkspaceService = runWorkspaceService;
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
    public void onEvent(BilledRunContext ctx, Ad4SseEvent event) {
        if (!ctx.getProfile().isBilledSku()) {
            return;
        }
        if (event == null || event.getName() != Ad4EventName.MESSAGE_DELTA) {
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
    public boolean onSuspended(BilledRunContext ctx, Consumer<Ad4SseEvent> sink) {
        if (!ctx.getProfile().isBilledSku()) {
            return false;
        }
        seedPlanCandidateFromTurn(ctx);
        persistOrEchoSkuPlan(ctx, sink);
        return true;
    }

    /**
     * 流式碎片未拼出可用策划时，用缓冲全文 / Turn 终稿 / 最近 assistant 消息补候选。
     * 产品顺序仍是「先策划、再 ask_human」；此处只修捕获。
     */
    private void seedPlanCandidateFromTurn(BilledRunContext ctx) {
        if (StringUtils.hasText(ctx.getAssistantTextCandidate())) {
            return;
        }
        if (captureSkuPlanCandidate(ctx.getAssistantTextBuffer(), ctx)) {
            return;
        }
        if (captureSkuPlanCandidate(ctx.getTurnFinalResponse(), ctx)) {
            return;
        }
        List<Message> messages = ctx.getTurnMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message == null || !"assistant".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            if (captureSkuPlanCandidate(message.getContent(), ctx)) {
                return;
            }
        }
    }

    private void persistOrEchoSkuPlan(BilledRunContext billedCtx, Consumer<Ad4SseEvent> sink) {
        GenerationRunContext context = billedCtx.getRun();
        String planText = billedCtx.getAssistantTextCandidate();
        String toolCallId = billedCtx.getPendingToolCallId();

        if (!StringUtils.hasText(planText)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ArtifactPersistPlugin.MSG_LISTING_PLAN_UNUSABLE);
        }
        ParsedGenerationOutput parsed = parsePlan(planText, billedCtx);
        if (parsed.getRawView() == null
                || !ArtifactPersistPlugin.isUsableSkuPlanPayload(parsed.getBusinessPayload())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ArtifactPersistPlugin.MSG_LISTING_PLAN_UNUSABLE);
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
            sink.accept(Ad4SseEvent.of(Ad4EventName.ARTIFACT_READY,
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
            parsed = generationOutputParser.parse(text, runDir(ctx));
        } catch (IllegalArgumentException ex) {
            // 指针已出现但文件未就绪：记下原文，挂起落库时再解析并走失败收尾。
            ctx.setAssistantTextCandidate(text.trim());
            return true;
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

    private Path runDir(BilledRunContext ctx) {
        GenerationRunContext run = ctx.getRun();
        return runWorkspaceService.runDir(run.getSessionId(), run.getRunId());
    }

    private ParsedGenerationOutput parsePlan(String text, BilledRunContext ctx) {
        try {
            return generationOutputParser.parse(text, runDir(ctx));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "成果文件无效");
        }
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

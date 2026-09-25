package com.xmut.ebus.application.business.agent.service;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.sse.Ad4EventName;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.application.business.agent.sse.PiEventToAd4Mapper;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.ObjectUtils;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AgentRuntime 编排：预占 → GenerationRun → AgentSession → AD-4 SSE。
 * <p>
 * 空跑只 {@code reserveOne} + 结束 {@code release}；禁止 {@code settle}；
 * 不发 {@code artifact_ready}/{@code run_settled}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApplicationService {

    static final String EMPTY_RUN_FAIL_REASON = "空跑无可用成果，预占已释放";
    static final String RELEASE_FAILED_REASON = "空跑结束但预占释放失败";
    static final String SSE_SEND_FAILED_RELEASED = "SSE 下发失败，预占已释放";
    static final String SSE_SEND_FAILED_RELEASE_FAILED = "SSE 下发失败且预占释放失败";

    private final CreditApplicationService creditApplicationService;
    private final GenerationRunRepository generationRunRepository;
    private final AgentSession agentSession;
    private final Clock clock;

    /**
     * 同步预占并落 GenerationRun；积分不足时抛错且不创建 Run（调用方勿已打开成功 SSE）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmptyRunContext prepareEmptyRun(StartEmptyRunCommand command) {
        ObjectUtils.requireNonNull(command, "空跑命令不能为空");
        String userId = StringUtils.requireHasText(command.getUserId(), "用户 ID 不能为空");

        String holdId = creditApplicationService.reserveOne(userId);
        String sessionId = StringUtils.hasText(command.getSessionId())
                ? command.getSessionId().trim()
                : UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String runId = UUID.randomUUID().toString();
        GenerationRun run = GenerationRun.start(runId, userId, holdId, sessionId, now);
        generationRunRepository.save(run);

        LoggerUtils.success(log, AgentApplicationService.class, "prepareEmptyRun",
                NameValue.create("userId", userId),
                NameValue.create("runId", runId),
                NameValue.create("holdId", holdId),
                NameValue.create("sessionId", sessionId));
        return new EmptyRunContext(runId, userId, holdId, sessionId);
    }

    /**
     * 流式空跑：{@code run_started} → Pi 进度映射 → 结束 release + {@code run_failed}。
     * 绝不调用 settle；异常路径同样尝试 release + {@code run_failed}。
     */
    public void streamEmptyRun(EmptyRunContext context, Consumer<Ad4SseEvent> sink) {
        ObjectUtils.requireNonNull(context, "空跑上下文不能为空");
        ObjectUtils.requireNonNull(sink, "SSE sink 不能为空");

        AtomicBoolean sawMessageDelta = new AtomicBoolean(false);
        AtomicBoolean aborted = new AtomicBoolean(false);
        AutoCloseable subscription = null;
        boolean released = false;
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_started, runStartedData(context)));

            subscription = agentSession.subscribe(new Consumer<PiEvent>() {
                @Override
                public void accept(PiEvent event) {
                    if (aborted.get()) {
                        return;
                    }
                    PiEventToAd4Mapper.mapProgress(event).ifPresent(mapped -> {
                        if (aborted.get()) {
                            return;
                        }
                        try {
                            if (mapped.getName() == Ad4EventName.message_delta) {
                                sawMessageDelta.set(true);
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
            });

            TurnResult result = agentSession.prompt(PromptRequest.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .text("empty-run")
                    .build());

            if (aborted.get()) {
                released = finishAborted(context, sink);
                return;
            }

            if (!sawMessageDelta.get()) {
                Map<String, Object> delta = new LinkedHashMap<String, Object>();
                delta.put("text", result != null && StringUtils.hasText(result.getFinalResponse())
                        ? result.getFinalResponse()
                        : "empty-run stub");
                emit(sink, Ad4SseEvent.of(Ad4EventName.message_delta, delta));
            }

            boolean releaseOk = releaseHoldSafe(context);
            released = releaseOk;
            markRunFailed(context.getRunId());
            emitRunFailedSafe(sink, releaseOk ? EMPTY_RUN_FAIL_REASON : RELEASE_FAILED_REASON);
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "streamEmptyRun",
                    ex.getMessage() != null ? ex.getMessage() : "stream failed",
                    NameValue.create("runId", context.getRunId()));
            if (!released) {
                boolean releaseOk = releaseHoldSafe(context);
                released = releaseOk;
                markRunFailed(context.getRunId());
                if (releaseOk) {
                    emitRunFailedSafe(sink, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "生成失败");
                } else {
                    emitRunFailedSafe(sink, RELEASE_FAILED_REASON);
                }
            } else {
                markRunFailed(context.getRunId());
                emitRunFailedSafe(sink, StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "生成失败");
            }
        } finally {
            closeQuietly(subscription);
        }
    }

    /**
     * 一站式：预占建 Run 后立刻流式（便于单测）。积分不足时不调 sink。
     */
    public EmptyRunContext startEmptyRun(StartEmptyRunCommand command, Consumer<Ad4SseEvent> sink) {
        EmptyRunContext context = prepareEmptyRun(command);
        streamEmptyRun(context, sink);
        return context;
    }

    private boolean finishAborted(EmptyRunContext context, Consumer<Ad4SseEvent> sink) {
        boolean releaseOk = releaseHoldSafe(context);
        markRunFailed(context.getRunId());
        emitRunFailedSafe(sink, releaseOk ? SSE_SEND_FAILED_RELEASED : SSE_SEND_FAILED_RELEASE_FAILED);
        return releaseOk;
    }

    /**
     * @return true 仅当 release 成功
     */
    private boolean releaseHoldSafe(EmptyRunContext context) {
        try {
            creditApplicationService.release(context.getUserId(), context.getHoldId());
            LoggerUtils.success(log, AgentApplicationService.class, "release",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("holdId", context.getHoldId()),
                    NameValue.create("runId", context.getRunId()));
            return true;
        } catch (BusinessException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占失败",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("holdId", context.getHoldId()));
            return false;
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, AgentApplicationService.class, "release",
                    ex.getMessage() != null ? ex.getMessage() : "释放预占异常",
                    NameValue.create("userId", context.getUserId()),
                    NameValue.create("holdId", context.getHoldId()));
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

    private static Map<String, Object> runStartedData(EmptyRunContext context) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("runId", context.getRunId());
        data.put("sessionId", context.getSessionId());
        data.put("holdId", context.getHoldId());
        return data;
    }

    private static Map<String, Object> failData(String reason) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("reason", reason);
        data.put("emptyRun", true);
        return data;
    }

    private static void emit(Consumer<Ad4SseEvent> sink, Ad4SseEvent event) {
        sink.accept(event);
    }

    private static void emitRunFailedSafe(Consumer<Ad4SseEvent> sink, String reason) {
        try {
            emit(sink, Ad4SseEvent.of(Ad4EventName.run_failed, failData(reason)));
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

package com.xmut.forma.interfaces.web.business.agent;

import com.xmut.forma.application.business.agent.command.ResumeGenerationRunCommand;
import com.xmut.forma.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.forma.application.business.agent.dto.GenerationRunContext;
import com.xmut.forma.application.business.agent.service.AgentApplicationService;
import com.xmut.forma.application.business.agent.sse.SseEvent;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.interfaces.security.SecuritySupport;
import com.xmut.forma.interfaces.vo.business.agent.ResumeGenerationRunRequest;
import com.xmut.forma.interfaces.vo.business.agent.StartGenerationRunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 计费生成 SSE：JWT 鉴权后直接返回 {@code text/event-stream}。
 * <p>
 * 预占失败（如积分不足）在打开流之前以 JSON 业务错误返回，避免 SSE produces 干扰统一异常出口。
 * 入口：{@code POST /runs}（blank skillId → 无 Skill；已知 skillId → 计费）与 {@code POST /runs/{runId}/resume}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final AgentApplicationService agentService;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-sse");
        t.setDaemon(true);
        return t;
    });

    /**
     * 通用 Generation Run：blank skillId → 无 Skill markdown；已知 skillId → 计费。
     */
    @PostMapping(value = "/runs")
    public Object streamGenerationRun(@RequestBody(required = false) StartGenerationRunRequest request) {
        String userId = SecuritySupport.requireUserId();
        StartGenerationRunRequest body = request != null ? request : new StartGenerationRunRequest();
        StartGenerationRunCommand command = StartGenerationRunCommand.builder()
                .userId(userId)
                .username(SecuritySupport.currentUsername())
                .text(body.getText())
                .sessionId(body.getSessionId())
                .sceneId(body.getSceneId())
                .sceneCode(body.getSceneCode())
                .skillId(body.getSkillId())
                .build();

        final GenerationRunContext context;
        try {
            context = agentService.prepareGenerationRun(command);
        } catch (BusinessException ex) {
            int status = ex.getErrorCode().getHttpStatus();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(status, ex.getMessage()));
        }

        final String runId = context.getRunId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final AtomicBoolean finishedNormally = new AtomicBoolean(false);
        emitter.onTimeout(() -> agentService.cancelRun(runId, "sse_timeout"));
        emitter.onError(ex -> agentService.cancelRun(runId, "sse_error"));
        emitter.onCompletion(() -> {
            if (!finishedNormally.get()) {
                agentService.cancelRun(runId, "sse_completed_early");
            }
        });
        sseExecutor.execute(() -> {
            try {
                agentService.streamBilledRun(context, event -> sendEvent(emitter, event));
                finishedNormally.set(true);
                emitter.complete();
            } catch (Exception ex) {
                log.warn("generation run sse failed runId={}: {}", runId, ex.toString());
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // already completed
                }
            }
        });
        return emitter;
    }

    /**
     * ask_human 续跑：返回新 SSE 续流（首段流在 {@code human_input_required} 后由 FE 停读）。
     * Body: {@code toolCallId} + {@code optionId}({@code confirm_execute}|{@code supplement}) + 可选 {@code freeText}；FE 应带 {@code confirmId}。
     */
    @PostMapping(value = "/runs/{runId}/resume")
    public Object resumeGenerationRun(@PathVariable("runId") String runId,
                                      @RequestBody(required = false) ResumeGenerationRunRequest request) {
        String userId = SecuritySupport.requireUserId();
        ResumeGenerationRunRequest body = request != null ? request : new ResumeGenerationRunRequest();
        ResumeGenerationRunCommand command = ResumeGenerationRunCommand.builder()
                .userId(userId)
                .username(SecuritySupport.currentUsername())
                .runId(runId)
                .toolCallId(body.getToolCallId())
                .optionId(body.getOptionId())
                .freeText(body.getFreeText())
                .confirmId(body.getConfirmId())
                .build();

        try {
            agentService.prepareResumeRun(command);
        } catch (BusinessException ex) {
            int status = ex.getErrorCode().getHttpStatus();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(status, ex.getMessage()));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final AtomicBoolean finishedNormally = new AtomicBoolean(false);
        emitter.onTimeout(() -> agentService.cancelRun(runId, "sse_timeout"));
        emitter.onError(ex -> agentService.cancelRun(runId, "sse_error"));
        emitter.onCompletion(() -> {
            if (!finishedNormally.get()) {
                agentService.cancelRun(runId, "sse_completed_early");
            }
        });
        sseExecutor.execute(() -> {
            try {
                agentService.resumeBilledRun(command, event -> sendEvent(emitter, event));
                finishedNormally.set(true);
                emitter.complete();
            } catch (Exception ex) {
                log.warn("generation run sse failed runId={}: {}", runId, ex.toString());
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // already completed
                }
            }
        });
        return emitter;
    }

    @PreDestroy
    void shutdownSseExecutor() {
        sseExecutor.shutdown();
    }

    private static void sendEvent(SseEmitter emitter, SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getWireName())
                    .data(event.getData()));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE send failed: " + event.getWireName(), ex);
        }
    }
}

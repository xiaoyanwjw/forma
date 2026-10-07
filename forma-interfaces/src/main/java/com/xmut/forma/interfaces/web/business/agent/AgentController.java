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
import com.xmut.forma.interfaces.vo.business.agent.StartPicklistRunRequest;
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
import java.util.function.Consumer;

/**
 * Agent 计费生成 SSE：JWT 鉴权后直接返回 {@code text/event-stream}。
 * <p>
 * 预占失败（如积分不足）在打开流之前以 JSON 业务错误返回，避免 SSE produces 干扰统一异常出口。
 * 通用入口 {@code POST /runs}；{@code /runs/picklist}、{@code /runs/listing} 为兼容别名。
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

        return openRunEmitter(context.getRunId(), emitter ->
                agentService.streamBilledRun(context, event -> sendEvent(emitter, event)));
    }

    /**
     * 计费选品别名：预占失败返回 JSON；成功则 SSE（artifact_ready / run_settled 或 run_failed）。
     */
    @PostMapping(value = "/runs/picklist")
    public Object startPicklistRun(@RequestBody StartPicklistRunRequest request) {
        StartPicklistRunRequest src = request != null ? request : new StartPicklistRunRequest();
        StartGenerationRunRequest body = new StartGenerationRunRequest();
        body.setText(src.getText());
        body.setSessionId(src.getSessionId());
        body.setSceneId(src.getSceneId());
        body.setSceneCode(src.getSceneCode());
        body.setSkillId("ecommerce-picklist");
        return streamGenerationRun(body);
    }

    /**
     * 计费 Listing 别名：预占失败返回 JSON；成功则 SSE（artifact_ready / run_settled 或 run_failed）。
     */
    @PostMapping(value = "/runs/listing")
    public Object startListingRun(@RequestBody StartPicklistRunRequest request) {
        StartPicklistRunRequest src = request != null ? request : new StartPicklistRunRequest();
        StartGenerationRunRequest body = new StartGenerationRunRequest();
        body.setText(src.getText());
        body.setSessionId(src.getSessionId());
        body.setSceneId(src.getSceneId());
        body.setSceneCode(src.getSceneCode());
        body.setSkillId("ecommerce-skulist");
        return streamGenerationRun(body);
    }

    /**
     * ask_human 续跑：返回新 SSE 续流（首段流在 {@code human_input_required} 后由 FE 停读）。
     * Body: {@code toolCallId} + {@code optionId}({@code confirm_execute}|{@code supplement}) + 可选 {@code freeText}。
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

        return openRunEmitter(runId, emitter ->
                agentService.resumeBilledRun(command, event -> sendEvent(emitter, event)));
    }

    @PreDestroy
    void shutdownSseExecutor() {
        sseExecutor.shutdown();
    }

    private SseEmitter openRunEmitter(String runId, Consumer<SseEmitter> stream) {
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
                stream.accept(emitter);
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

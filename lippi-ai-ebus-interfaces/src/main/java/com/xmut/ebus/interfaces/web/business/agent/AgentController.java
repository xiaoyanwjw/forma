package com.xmut.ebus.interfaces.web.business.agent;

import com.xmut.ebus.application.business.agent.command.StartEmptyRunCommand;
import com.xmut.ebus.application.business.agent.command.StartPicklistRunCommand;
import com.xmut.ebus.application.business.agent.dto.EmptyRunContext;
import com.xmut.ebus.application.business.agent.dto.PicklistRunContext;
import com.xmut.ebus.application.business.agent.service.AgentApplicationService;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import com.xmut.ebus.interfaces.vo.business.agent.StartPicklistRunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 计费生成 SSE：JWT 鉴权后直接返回 {@code text/event-stream}。
 * <p>
 * 预占失败（如积分不足）在打开流之前以 JSON 业务错误返回，避免 SSE produces 干扰统一异常出口。
 * 空跑永不 settle；计费选品走独立入口。
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
     * 空跑：预占失败返回 JSON 业务错误；成功则打开 SSE。永不 settle。
     *
     * @param sessionId 可选，复用同一聊天 session（每次仍新 hold）
     * @param sceneId   场景业务 UUID；与 sceneCode 至少一项
     * @param sceneCode 稳定场景码；与 sceneId 至少一项
     */
    @PostMapping(value = "/runs/empty")
    public Object startEmptyRun(@RequestParam(value = "sessionId", required = false) String sessionId,
                                @RequestParam(value = "sceneId", required = false) String sceneId,
                                @RequestParam(value = "sceneCode", required = false) String sceneCode) {
        String userId = SecuritySupport.requireUserId();
        StartEmptyRunCommand command = StartEmptyRunCommand.builder()
                .userId(userId)
                .username(SecuritySupport.currentUsername())
                .sessionId(sessionId)
                .sceneId(sceneId)
                .sceneCode(sceneCode)
                .build();

        final EmptyRunContext context;
        try {
            context = agentService.prepareEmptyRun(command);
        } catch (BusinessException ex) {
            int status = ex.getErrorCode().getHttpStatus();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(status, ex.getMessage()));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                agentService.streamEmptyRun(context, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("empty run sse failed runId={}: {}", context.getRunId(), ex.toString());
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
     * 计费选品：预占失败返回 JSON；成功则 SSE（artifact_ready / run_settled 或 run_failed）。
     */
    @PostMapping(value = "/runs/picklist")
    public Object startPicklistRun(@RequestBody StartPicklistRunRequest request) {
        String userId = SecuritySupport.requireUserId();
        StartPicklistRunRequest body = request != null ? request : new StartPicklistRunRequest();
        StartPicklistRunCommand command = StartPicklistRunCommand.builder()
                .userId(userId)
                .username(SecuritySupport.currentUsername())
                .text(body.getText())
                .sessionId(body.getSessionId())
                .sceneId(body.getSceneId())
                .sceneCode(body.getSceneCode())
                .build();

        final PicklistRunContext context;
        try {
            context = agentService.preparePicklistRun(command);
        } catch (BusinessException ex) {
            int status = ex.getErrorCode().getHttpStatus();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(status, ex.getMessage()));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                agentService.streamPicklistRun(context, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("picklist run sse failed runId={}: {}", context.getRunId(), ex.toString());
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

    private static void sendEvent(SseEmitter emitter, Ad4SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getWireName())
                    .data(event.getData()));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE send failed: " + event.getWireName(), ex);
        }
    }
}

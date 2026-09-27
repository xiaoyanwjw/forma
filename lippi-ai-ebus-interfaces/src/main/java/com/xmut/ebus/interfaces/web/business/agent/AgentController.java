package com.xmut.ebus.interfaces.web.business.agent;

import com.xmut.ebus.application.business.agent.command.StartGenerationRunCommand;
import com.xmut.ebus.application.business.agent.dto.GenerationRunContext;
import com.xmut.ebus.application.business.agent.service.AgentApplicationService;
import com.xmut.ebus.application.business.agent.sse.Ad4SseEvent;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import com.xmut.ebus.interfaces.vo.business.agent.StartGenerationRunRequest;
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
 * 通用入口 {@code POST /runs}；{@code /runs/empty}、{@code /runs/picklist}、{@code /runs/listing} 为兼容别名。
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
     * 通用 Generation Run：dryRun=true 永不 settle；blank skillId → 无 Skill markdown；
     * 已知 skillId → 计费（ecommerce-picklist / ecommerce-skulist）。
     */
    @PostMapping(value = "/runs")
    public Object startGenerationRun(@RequestBody(required = false) StartGenerationRunRequest request) {
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
                .dryRun(body.isDryRun())
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

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                agentService.streamGenerationRun(context, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("generation run sse failed runId={}: {}", context.getRunId(), ex.toString());
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
     * 空跑别名：预占失败返回 JSON 业务错误；成功则打开 SSE。永不 settle。
     *
     * @param sessionId 可选，复用同一聊天 session（每次仍新 hold）
     * @param sceneId   场景业务 UUID；与 sceneCode 至少一项
     * @param sceneCode 稳定场景码；与 sceneId 至少一项
     */
    @PostMapping(value = "/runs/empty")
    public Object startEmptyRun(@RequestParam(value = "sessionId", required = false) String sessionId,
                                @RequestParam(value = "sceneId", required = false) String sceneId,
                                @RequestParam(value = "sceneCode", required = false) String sceneCode) {
        StartGenerationRunRequest body = new StartGenerationRunRequest();
        body.setSessionId(sessionId);
        body.setSceneId(sceneId);
        body.setSceneCode(sceneCode);
        body.setDryRun(true);
        return startGenerationRun(body);
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
        body.setDryRun(false);
        return startGenerationRun(body);
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
        body.setDryRun(false);
        return startGenerationRun(body);
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

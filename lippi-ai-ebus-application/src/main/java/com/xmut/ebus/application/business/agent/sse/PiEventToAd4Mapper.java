package com.xmut.ebus.application.business.agent.sse;

import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pi 内部事件 → AD-4 闭合事件名。
 * <p>
 * 声明全集（本类 + {@link Ad4EventName}）；空跑路径不发出 {@code artifact_ready}/{@code run_settled}。
 * {@code run_started}/{@code run_failed}/{@code run_settled} 由编排层显式发送。
 * <p>
 * 进度 payload：{@code agent_started}/{@code agent_ended} 带展示用 {@code label}；
 * 工具事件带 {@code toolName}/{@code toolCallId}；文本 delta 带 {@code text}。
 */
public final class PiEventToAd4Mapper {

    private PiEventToAd4Mapper() {
    }

    /**
     * 映射可流式进度类事件；无法映射则 empty（调用方勿把 Pi 名直接下发）。
     */
    public static Optional<Ad4SseEvent> mapEvent(PiEvent event) {
        if (event == null || event.getType() == null) {
            return Optional.empty();
        }
        PiEventType type = event.getType();
        if (type == PiEventType.AGENT_START) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.agent_started, labelMap("agent.start")));
        }
        if (type == PiEventType.AGENT_END) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.agent_ended, labelMap("agent.end")));
        }
        if (type == PiEventType.MESSAGE_UPDATE) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.message_delta, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_START) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.tool_started, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_END) {
            return Optional.of(Ad4SseEvent.of(Ad4EventName.tool_finished, payloadMap(event.getPayload())));
        }
        return Optional.empty();
    }

    /** 供测试与类型声明：AD-4 事件名全集。 */
    public static Ad4EventName[] declaredEventNames() {
        return Ad4EventName.values();
    }

    private static Map<String, Object> labelMap(String label) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("label", label);
        return data;
    }

    private static Map<String, Object> payloadMap(Object payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (payload instanceof ToolCallEntry) {
            ToolCallEntry call = (ToolCallEntry) payload;
            putIfText(data, "toolName", call.getToolName());
            putIfText(data, "toolCallId", call.getId());
            return data.isEmpty() ? Collections.<String, Object>emptyMap() : data;
        }
        if (payload instanceof ToolResult) {
            ToolResult result = (ToolResult) payload;
            putIfText(data, "toolName", result.getToolName());
            putIfText(data, "toolCallId", result.getCallId());
            return data.isEmpty() ? Collections.<String, Object>emptyMap() : data;
        }
        if (payload instanceof CharSequence) {
            putIfText(data, "text", payload.toString());
            return data.isEmpty() ? Collections.<String, Object>emptyMap() : data;
        }
        data.put("payload", String.valueOf(payload));
        return data;
    }

    private static void putIfText(Map<String, Object> data, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            data.put(key, value.trim());
        }
    }
}

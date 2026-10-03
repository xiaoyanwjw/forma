package com.xmut.forma.application.business.agent.sse;

import com.xmut.lims.pi.agent.tool.base.AskHumanToolHandler;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.event.ToolSuspendPayload;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.session.TurnResult;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pi 内部事件 → AD-4 闭合事件名。
 * <p>
 * 声明全集（本类 + {@link SseEventName}）；空跑路径不发出 {@code artifact_ready}/{@code run_settled}。
 * {@code run_started}/{@code run_failed}/{@code run_settled} 由编排层显式发送。
 * <p>
 * 进度 payload：{@code agent_started}/{@code agent_ended} 带展示用 {@code label}；
 * 工具事件带 {@code toolName}/{@code toolCallId}；{@code tool_finished} 另带
 * {@code success} 与截断后的 {@code output}/{@code error}；文本 delta 带 {@code text}。
 * {@code ask_human} 挂起映射为 {@code human_input_required}（AD-S12）。
 */
public final class PiEventMapper {

    /** Keep SSE payloads bounded when skill bodies / search dumps are large. */
    static final int MAX_TOOL_TEXT_CHARS = 12_000;

    private PiEventMapper() {
    }

    /**
     * 映射可流式进度类事件；无法映射则 empty（调用方勿把 Pi 名直接下发）。
     */
    public static Optional<SseEvent> mapEvent(PiEvent event) {
        if (event == null || event.getType() == null) {
            return Optional.empty();
        }
        PiEventType type = event.getType();
        if (type == PiEventType.AGENT_START) {
            return Optional.of(SseEvent.of(SseEventName.AGENT_STARTED, labelMap("agent.start")));
        }
        if (type == PiEventType.AGENT_END) {
            return Optional.of(SseEvent.of(SseEventName.AGENT_ENDED, labelMap("agent.end")));
        }
        if (type == PiEventType.MESSAGE_UPDATE) {
            return Optional.of(SseEvent.of(SseEventName.MESSAGE_DELTA, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_START) {
            return Optional.of(SseEvent.of(SseEventName.TOOL_STARTED, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.TOOL_EXECUTION_END) {
            return Optional.of(SseEvent.of(SseEventName.TOOL_FINISHED, payloadMap(event.getPayload())));
        }
        if (type == PiEventType.SUSPENDED) {
            return mapHumanInputRequired(event.getPayload());
        }
        return Optional.empty();
    }

    /** 供测试与类型声明：AD-4 事件名全集。 */
    public static SseEventName[] declaredEventNames() {
        return SseEventName.values();
    }

    private static Optional<SseEvent> mapHumanInputRequired(Object payload) {
        ToolCallEntry call = null;
        String runId = null;
        if (payload instanceof ToolSuspendPayload) {
            ToolSuspendPayload suspend = (ToolSuspendPayload) payload;
            call = suspend.getCall();
            runId = suspend.getRunId();
        } else if (payload instanceof ToolCallEntry) {
            call = (ToolCallEntry) payload;
        } else if (payload instanceof TurnResult) {
            return Optional.empty();
        }
        if (call == null || !isAskHuman(call.getToolName())) {
            return Optional.empty();
        }
        AskHumanToolHandler.ParsedAsk parsed = null;
        if (payload instanceof ToolSuspendPayload) {
            ToolSuspendPayload suspend = (ToolSuspendPayload) payload;
            if (suspend.getResult() != null
                    && suspend.getResult().getOutput() != null
                    && !suspend.getResult().getOutput().trim().isEmpty()) {
                parsed = AskHumanToolHandler.parseOutput(suspend.getResult().getOutput());
            }
        }
        if (parsed == null) {
            parsed = AskHumanToolHandler.parse(call);
        }
        if (parsed == null) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("question", parsed.getQuestion());
        data.put("options", parsed.getOptions());
        data.put("allowFreeText", Boolean.valueOf(parsed.isAllowFreeText()));
        putIfText(data, "toolCallId", call.getId());
        putIfText(data, "runId", runId);
        return Optional.of(SseEvent.of(SseEventName.HUMAN_INPUT_REQUIRED, data));
    }

    private static boolean isAskHuman(String toolName) {
        return toolName != null && ToolPolicyExtension.ASK_HUMAN_TOOL.equals(toolName.trim());
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
            data.put("success", Boolean.valueOf(result.isSuccess()));
            if (result.isSuccess()) {
                putIfText(data, "output", truncateToolText(result.getOutput()));
            } else {
                putIfText(data, "error", truncateToolText(result.getErrorMessage()));
            }
            return data.isEmpty() ? Collections.<String, Object>emptyMap() : data;
        }
        if (payload instanceof CharSequence) {
            // 流式 delta 常单独下发空格/"\n"；不可 trim，否则英文词间空格被吞
            String text = payload.toString();
            if (!text.isEmpty()) {
                data.put("text", text);
            }
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

    static String truncateToolText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_TOOL_TEXT_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TOOL_TEXT_CHARS) + "\n…(truncated)";
    }
}

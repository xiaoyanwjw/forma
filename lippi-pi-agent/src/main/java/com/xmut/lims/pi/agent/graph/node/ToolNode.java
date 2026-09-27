package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.event.AfterToolCallResult;
import com.xmut.lims.pi.agent.event.BeforeToolCallPayload;
import com.xmut.lims.pi.agent.event.BeforeToolCallResult;
import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.event.ToolSuspendPayload;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Tools 执行节点。
 * 功能描述：按开源 Pi 顺序逐个执行 TOOL_CALLS，结果写入 MESSAGES。
 */
public final class ToolNode implements GraphNode {

    private static final Logger log = LoggerFactory.getLogger(ToolNode.class);

    private final Map<String, ToolHandler> handlers;

    public ToolNode() {
        this(Collections.emptyMap());
    }

    public ToolNode(Map<String, ToolHandler> handlers) {
        this.handlers = Collections.unmodifiableMap(new HashMap<>(handlers));
    }

    @Override
    public Map<String, Object> execute(GraphState state, NodeContext ctx) {
        Object raw = state.get(StateKeys.TOOL_CALLS);
        ToolContext toolCtx = ToolContext.from(ctx, state);
        List<ToolResult> results = new ArrayList<>();
        List<ToolCallEntry> remaining = new ArrayList<>();
        Map<String, Object> updates = new HashMap<>();
        boolean interrupted = false;
        final String turnId = turnIdOf(state);

        if (raw instanceof List) {
            List<?> calls = (List<?>) raw;
            for (int i = 0; i < calls.size(); i++) {
                Object item = calls.get(i);
                if (!(item instanceof ToolCallEntry)) {
                    ToolResult invalid = ToolResult.failed(null, null, "Invalid tool call entry: "
                            + (item == null ? "null" : item.getClass().getName()));
                    results.add(afterToolCall(invalid, ctx));
                    onToolExecutionEnd(ctx, invalid, turnId);
                    onToolMessages(ctx, invalid);
                    continue;
                }
                ToolCallEntry call = (ToolCallEntry) item;

                // [回调] tool_execution_start
                onToolExecutionStart(ctx, call, turnId);

                // [回调] before_tool_call（可 block / HITL）
                BeforeToolCallResult gate = beforeToolCall(call, state, ctx);
                if (gate.isNeedsHitl()) {
                    remaining.add(call);
                    appendRemaining(calls, i + 1, remaining);
                    interrupted = true;
                    onSuspended(ctx, call, gate.getReason() != null ? gate.getReason() : "awaiting approval");
                    break;
                }

                ToolResult result;
                if (gate.isBlock()) {
                    result = ToolResult.failed(call.getId(), call.getToolName(),
                            gate.getReason() != null ? gate.getReason() : "blocked");
                } else {
                    result = executeHandler(call, toolCtx);
                }

                // [回调] after_tool_call → tool_execution_end → message_*
                result = afterToolCall(result, ctx);
                results.add(result);
                onToolExecutionEnd(ctx, result, turnId);
                onToolMessages(ctx, result);
            }
        }

        if (!results.isEmpty()) {
            updates.put(StateKeys.MESSAGES,
                    Message.withToolResults(state.get(StateKeys.MESSAGES), results));
        }
        updates.put(StateKeys.TOOL_RESULTS, Collections.emptyList());
        updates.put(StateKeys.TOOL_CALLS, remaining);
        if (state.get(StateKeys.TOOL_APPROVAL) != null) {
            updates.put(StateKeys.TOOL_APPROVAL, null);
            updates.put(StateKeys.HUMAN_INPUT, null);
        }
        if (interrupted) {
            updates.put(StateKeys.INTERRUPT, Boolean.TRUE);
            updates.put(StateKeys.TOOL_POLICY_ROUTE, ToolPolicyExtension.ROUTE_NEEDS_HITL);
        }
        return updates;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> activeToolsOf(GraphState state) {
        if (state == null) {
            return null;
        }
        Object raw = state.get(StateKeys.ACTIVE_TOOLS);
        if (raw instanceof Collection) {
            return (Collection<String>) raw;
        }
        return null;
    }

    private String turnIdOf(GraphState state) {
        Object raw = state.get(StateKeys.CURRENT_TURN_ID);
        if (raw instanceof String) {
            String id = ((String) raw).trim();
            return id.isEmpty() ? null : id;
        }
        return null;
    }

    /**
     * [回调] tool_execution_start
     */
    private void onToolExecutionStart(NodeContext ctx, ToolCallEntry call, String turnId) {
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(PiEvent.of(PiEventType.TOOL_EXECUTION_START, call, turnId));
        } catch (RuntimeException ex) {
            log.warn("emitter emit TOOL_EXECUTION_START failed: {}", ex.toString());
        }
    }

    /**
     * [回调] before_tool_call — 归约 block / needsHitl / allow
     */
    private BeforeToolCallResult beforeToolCall(ToolCallEntry call, GraphState state, NodeContext ctx) {
        BeforeToolCallResult fromBus = emitAndReduce(
                ctx,
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL,
                        BeforeToolCallPayload.of(
                                call,
                                state.get(StateKeys.TOOL_APPROVAL),
                                state.get(StateKeys.HUMAN_INPUT),
                                activeToolsOf(state))),
                BeforeToolCallResult.class);
        return fromBus != null ? fromBus : BeforeToolCallResult.allow();
    }

    /**
     * [回调] after_tool_call — 可改写 ToolResult
     */
    private ToolResult afterToolCall(ToolResult result, NodeContext ctx) {
        AfterToolCallResult reduced = emitAndReduce(
                ctx, PiEvent.of(PiEventType.AFTER_TOOL_CALL, result), AfterToolCallResult.class);
        if (reduced != null && reduced.getToolResult() != null) {
            return reduced.getToolResult();
        }
        return result;
    }

    /**
     * [回调] tool_execution_end
     */
    private void onToolExecutionEnd(NodeContext ctx, ToolResult result, String turnId) {
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(PiEvent.of(PiEventType.TOOL_EXECUTION_END, result, turnId));
        } catch (RuntimeException ex) {
            log.warn("emitter emit TOOL_EXECUTION_END failed: {}", ex.toString());
        }
    }

    /**
     * [回调] message_start / message_end（toolResult）
     */
    private void onToolMessages(NodeContext ctx, ToolResult result) {
        Message toolMessage = toolMessage(result);
        if (toolMessage == null) {
            return;
        }
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(PiEvent.of(PiEventType.MESSAGE_START, toolMessage));
            emitter.emit(PiEvent.of(PiEventType.MESSAGE_END, toolMessage));
        } catch (RuntimeException ex) {
            log.warn("emitter emit tool messages failed: {}", ex.toString());
        }
    }

    /**
     * [回调] suspended — HITL（ask_human / WRITE）；payload 带 call 供 SSE 映射。
     */
    private void onSuspended(NodeContext ctx, ToolCallEntry call, String reason) {
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return;
        }
        try {
            String runId = ctx != null ? ctx.getRunId() : null;
            emitter.emit(PiEvent.of(PiEventType.SUSPENDED, ToolSuspendPayload.of(call, runId, reason)));
        } catch (RuntimeException ex) {
            log.warn("emitter emit SUSPENDED failed: {}", ex.toString());
        }
    }

    private ToolResult executeHandler(ToolCallEntry call, ToolContext toolCtx) {
        String toolName = call.getToolName() == null ? null : call.getToolName().trim();
        ToolHandler handler = (toolName == null || toolName.isEmpty()) ? null : handlers.get(toolName);
        if (handler == null) {
            return ToolResult.failed(call.getId(), call.getToolName(),
                    "Unknown tool: " + call.getToolName());
        }
        try {
            ToolResult result = handler.handle(call, toolCtx);
            if (result == null) {
                return ToolResult.failed(call.getId(), call.getToolName(), "null result");
            }
            return result;
        } catch (RuntimeException ex) {
            return ToolResult.failed(call.getId(), call.getToolName(),
                    "Tool handler error: " + ex.getMessage());
        }
    }

    private static Message toolMessage(ToolResult result) {
        if (result == null || result.getCallId() == null || result.getCallId().trim().isEmpty()) {
            return null;
        }
        String content = result.isSuccess()
                ? (result.getOutput() != null ? result.getOutput() : "")
                : ("ERROR: " + (result.getErrorMessage() != null ? result.getErrorMessage() : "unknown"));
        return Message.tool(result.getCallId(), content);
    }

    private static void appendRemaining(List<?> calls, int from, List<ToolCallEntry> remaining) {
        for (int j = from; j < calls.size(); j++) {
            Object item = calls.get(j);
            if (item instanceof ToolCallEntry) {
                remaining.add((ToolCallEntry) item);
            }
        }
    }

    private static <T> T emitAndReduce(NodeContext ctx, PiEvent event, Class<T> resultType) {
        Emitter emitter = emitterOf(ctx);
        if (emitter == null) {
            return null;
        }
        try {
            return emitter.emit(event, resultType);
        } catch (RuntimeException ex) {
            log.warn("emitter emitAndReduce failed: {}", ex.toString());
            return null;
        }
    }

    private static Emitter emitterOf(NodeContext ctx) {
        return ctx != null ? ctx.getEmitter() : null;
    }
}

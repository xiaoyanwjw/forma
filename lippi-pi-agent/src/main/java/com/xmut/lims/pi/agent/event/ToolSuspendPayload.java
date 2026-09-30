package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import lombok.Value;

/**
 * {@link PiEventType#SUSPENDED} payload from ToolNode HITL.
 * Distinct from session-level {@code TurnResult} SUSPENDED.
 *
 * <p>{@code result} 可空：policy 提前 needsHitl 时无 handler 结果；
 * handler 返回 {@link ToolResult#isInterrupt()} 时带上规范化 output。
 */
@Value
public class ToolSuspendPayload {

    String reason;
    ToolCallEntry call;
    String runId;
    ToolResult result;

    public static ToolSuspendPayload of(ToolCallEntry call, String runId, String reason) {
        return new ToolSuspendPayload(reason, call, runId, null);
    }

    public static ToolSuspendPayload of(ToolCallEntry call, String runId, String reason, ToolResult result) {
        return new ToolSuspendPayload(reason, call, runId, result);
    }
}

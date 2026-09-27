package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import lombok.Value;

/**
 * {@link PiEventType#SUSPENDED} payload from ToolNode HITL.
 * Distinct from session-level {@code TurnResult} SUSPENDED.
 */
@Value
public class ToolSuspendPayload {

    String reason;
    ToolCallEntry call;
    String runId;

    public static ToolSuspendPayload of(ToolCallEntry call, String runId, String reason) {
        return new ToolSuspendPayload(reason, call, runId);
    }
}

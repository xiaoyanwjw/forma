package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.ai.tool.ToolResult;
import lombok.Value;

/**
 * Reduced result of {@link PiEventType#AFTER_TOOL_CALL}.
 * Last non-null write wins when multiple {@code on} handlers run.
 */
@Value
public class AfterToolCallResult {

    ToolResult toolResult;

    public static AfterToolCallResult of(ToolResult toolResult) {
        return new AfterToolCallResult(toolResult);
    }
}

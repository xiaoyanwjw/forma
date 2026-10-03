package com.xmut.forma.pi.agent.event;

import com.xmut.forma.pi.ai.tool.ToolResult;
import lombok.Value;

/**
 * AFTER_TOOL_CALL 归约结果。
 * 功能描述：可改写工具执行结果。
 */
@Value
public class AfterToolCallResult {

    ToolResult toolResult;

    public static AfterToolCallResult of(ToolResult toolResult) {
        return new AfterToolCallResult(toolResult);
    }
}

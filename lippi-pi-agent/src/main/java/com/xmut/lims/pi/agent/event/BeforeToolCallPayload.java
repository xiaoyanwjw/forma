package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import lombok.Value;

/**
 * BEFORE_TOOL_CALL 载荷。
 * 功能描述：携带当前 tool call 及执行上下文。
 */
@Value
public class BeforeToolCallPayload {

    ToolCallEntry call;
    Object toolApproval;
    Object humanInput;

    public static BeforeToolCallPayload of(ToolCallEntry call, Object toolApproval, Object humanInput) {
        return new BeforeToolCallPayload(call, toolApproval, humanInput);
    }

    public static ToolCallEntry callOf(PiEvent event) {
        if (event == null) {
            return null;
        }
        Object payload = event.getPayload();
        if (payload instanceof BeforeToolCallPayload) {
            return ((BeforeToolCallPayload) payload).getCall();
        }
        if (payload instanceof ToolCallEntry) {
            return (ToolCallEntry) payload;
        }
        return null;
    }

    public static BeforeToolCallPayload from(PiEvent event) {
        if (event == null) {
            return null;
        }
        Object payload = event.getPayload();
        if (payload instanceof BeforeToolCallPayload) {
            return (BeforeToolCallPayload) payload;
        }
        if (payload instanceof ToolCallEntry) {
            return of((ToolCallEntry) payload, null, null);
        }
        return null;
    }
}

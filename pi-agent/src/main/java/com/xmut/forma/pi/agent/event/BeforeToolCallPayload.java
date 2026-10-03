package com.xmut.forma.pi.agent.event;

import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import lombok.Value;

import java.util.Collection;

/**
 * BEFORE_TOOL_CALL 载荷。
 * 功能描述：携带当前 tool call 及执行上下文。
 */
@Value
public class BeforeToolCallPayload {

    ToolCallEntry call;
    Object toolApproval;
    Object humanInput;
    /** 本轮 active 工具名；null = 不按名裁剪（只拦未注册）。 */
    Collection<String> activeTools;

    public static BeforeToolCallPayload of(ToolCallEntry call, Object toolApproval, Object humanInput) {
        return of(call, toolApproval, humanInput, null);
    }

    public static BeforeToolCallPayload of(ToolCallEntry call,
                                           Object toolApproval,
                                           Object humanInput,
                                           Collection<String> activeTools) {
        return new BeforeToolCallPayload(call, toolApproval, humanInput, activeTools);
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

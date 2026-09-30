package com.xmut.lims.pi.ai.tool;

/**
 * 工具执行结果（hermes 最小结构）。
 *
 * <p>{@link #interrupt(String, String, String)}：handler 已跑完并写入 tool 回执后请求 HITL；
 * ToolNode 仍落 messages，call 视为完成；resume 将人答作为 user 消息续跑。
 */
public final class ToolResult {

    private final String callId;
    private final String toolName;
    private final boolean success;
    private final String output;
    private final String errorMessage;
    private final boolean interrupt;

    private ToolResult(String callId, String toolName, boolean success, String output,
                       String errorMessage, boolean interrupt) {
        this.callId = callId;
        this.toolName = toolName;
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
        this.interrupt = interrupt;
    }

    public static ToolResult ok(String callId, String toolName, String output) {
        return new ToolResult(callId, toolName, true, output, null, false);
    }

    /**
     * 成功执行且请求挂起（如 ask_human）：{@code success=true}、{@code interrupt=true}。
     */
    public static ToolResult interrupt(String callId, String toolName, String output) {
        return new ToolResult(callId, toolName, true, output, null, true);
    }

    public static ToolResult failed(String callId, String toolName, String errorMessage) {
        return new ToolResult(callId, toolName, false, null, errorMessage, false);
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /** 为 true 时 ToolNode 在写入 tool 回执后 interrupt（call 已完成）。 */
    public boolean isInterrupt() {
        return interrupt;
    }
}

package com.xmut.lims.pi.ai.tool;

/**
 * 工具执行结果（hermes 最小结构）。
 */
public final class ToolResult {

    private final String callId;
    private final String toolName;
    private final boolean success;
    private final String output;
    private final String errorMessage;

    private ToolResult(String callId, String toolName, boolean success, String output, String errorMessage) {
        this.callId = callId;
        this.toolName = toolName;
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    public static ToolResult ok(String callId, String toolName, String output) {
        return new ToolResult(callId, toolName, true, output, null);
    }

    public static ToolResult failed(String callId, String toolName, String errorMessage) {
        return new ToolResult(callId, toolName, false, null, errorMessage);
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
}

package com.xmut.lims.pi.agent.tool;

/**
 * 工具策略审计事件。
 * 功能描述：记录 FORBIDDEN / SUSPEND / APPROVE / DENY / EXECUTE 等决策。
 */
public final class ToolAuditEvent {

    public enum Kind {
        FORBIDDEN,
        SUSPEND,
        APPROVE,
        DENY,
        EXECUTE
    }

    private final Kind kind;
    private final String toolName;
    private final String callId;
    private final String runId;
    private final String traceId;
    private final String detail;

    public ToolAuditEvent(Kind kind,
                          String toolName,
                          String callId,
                          String runId,
                          String traceId,
                          String detail) {
        this.kind = kind;
        this.toolName = toolName;
        this.callId = callId;
        this.runId = runId;
        this.traceId = traceId;
        this.detail = detail;
    }

    public static ToolAuditEvent of(Kind kind, String toolName, String callId,
                                    ToolContext ctx, String detail) {
        return new ToolAuditEvent(
                kind,
                toolName,
                callId,
                ctx != null ? ctx.getRunId() : null,
                ctx != null ? ctx.getTraceId() : null,
                detail);
    }

    public Kind getKind() {
        return kind;
    }

    public String getToolName() {
        return toolName;
    }

    public String getCallId() {
        return callId;
    }

    public String getRunId() {
        return runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDetail() {
        return detail;
    }
}

package com.xmut.ebus.application.business.agent.dto;

/**
 * 预占 + 建 Run 后的上下文，供 SSE 流阶段使用。
 */
public final class EmptyRunContext {

    private final String runId;
    private final String userId;
    private final String holdId;
    private final String sessionId;

    public EmptyRunContext(String runId, String userId, String holdId, String sessionId) {
        this.runId = runId;
        this.userId = userId;
        this.holdId = holdId;
        this.sessionId = sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getUserId() {
        return userId;
    }

    public String getHoldId() {
        return holdId;
    }

    public String getSessionId() {
        return sessionId;
    }
}

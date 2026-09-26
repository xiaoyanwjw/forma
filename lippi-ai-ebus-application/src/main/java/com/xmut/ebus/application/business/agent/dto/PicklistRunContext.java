package com.xmut.ebus.application.business.agent.dto;

/**
 * 计费选品：预占 + 建 Run 后的上下文，供 SSE 流阶段使用。
 */
public final class PicklistRunContext {

    private final String runId;
    private final String userId;
    private final String holdId;
    private final String sessionId;
    private final String sceneCode;
    private final String promptText;

    public PicklistRunContext(String runId,
                              String userId,
                              String holdId,
                              String sessionId,
                              String sceneCode,
                              String promptText) {
        this.runId = runId;
        this.userId = userId;
        this.holdId = holdId;
        this.sessionId = sessionId;
        this.sceneCode = sceneCode;
        this.promptText = promptText;
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

    public String getSceneCode() {
        return sceneCode;
    }

    public String getPromptText() {
        return promptText;
    }
}

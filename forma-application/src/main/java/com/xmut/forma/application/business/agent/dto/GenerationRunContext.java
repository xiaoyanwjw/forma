package com.xmut.forma.application.business.agent.dto;

import com.xmut.forma.application.business.agent.support.SkillRunProfile;
import com.xmut.forma.common.util.StringUtils;

/**
 * Unified GenerationRun context for SSE streaming (dry / no-skill / billed).
 * <p>
 * HITL：{@link #artifactRef} 复用挂起 settle / 终态成果；{@link #holdId} 为当前未关闭预占。
 */
public final class GenerationRunContext {

    private final String runId;
    private final String userId;
    private String holdId;
    private final String sessionId;
    private final String sceneCode;
    private final String promptText;
    private final SkillRunProfile profile;

    private String artifactRef;
    private String execHoldId;
    private String pendingToolCallId;

    public GenerationRunContext(String runId,
                                String userId,
                                String holdId,
                                String sessionId,
                                String sceneCode,
                                String promptText,
                                SkillRunProfile profile) {
        this.runId = runId;
        this.userId = userId;
        this.holdId = holdId;
        this.sessionId = sessionId;
        this.sceneCode = sceneCode;
        this.promptText = promptText;
        this.profile = profile;
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

    public void setHoldId(String holdId) {
        this.holdId = holdId;
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

    public SkillRunProfile getProfile() {
        return profile;
    }

    /** 挂起路径已 settle：有 artifactRef，当前 hold 已清，回合仍 RUNNING。 */
    public boolean isSettledOnSuspended() {
        return StringUtils.hasText(artifactRef);
    }

    public void markSettledOnSuspended(String artifactRef) {
        this.artifactRef = artifactRef;
        this.holdId = null;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getExecHoldId() {
        return execHoldId;
    }

    public void bindExecHold(String execHoldId) {
        this.execHoldId = execHoldId;
        this.holdId = execHoldId;
    }

    public void clearExecHold() {
        this.execHoldId = null;
        this.holdId = null;
    }

    public String getPendingToolCallId() {
        return pendingToolCallId;
    }

    public void setPendingToolCallId(String pendingToolCallId) {
        this.pendingToolCallId = pendingToolCallId;
    }
}

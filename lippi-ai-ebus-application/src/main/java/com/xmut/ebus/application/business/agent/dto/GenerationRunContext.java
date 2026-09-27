package com.xmut.ebus.application.business.agent.dto;

import com.xmut.ebus.application.business.agent.support.SkillRunProfile;

/**
 * Unified GenerationRun context for SSE streaming (dry / no-skill / billed).
 * <p>
 * Listing HITL（策划分镜）另跟踪 {@link #planSettled} / {@link #execHoldId}；
 * {@link #holdId} 表示<strong>当前未关闭</strong>的预占（策划 settle 后清空，确认执行后再写入 exec hold）。
 */
public final class GenerationRunContext {

    private final String runId;
    private final String userId;
    private String holdId;
    private final String sessionId;
    private final String sceneCode;
    private final String promptText;
    private final SkillRunProfile profile;

    private boolean planSettled;
    private String planArtifactRef;
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

    public boolean isPlanSettled() {
        return planSettled;
    }

    public void markPlanSettled(String planArtifactRef) {
        this.planSettled = true;
        this.planArtifactRef = planArtifactRef;
        this.holdId = null;
    }

    public String getPlanArtifactRef() {
        return planArtifactRef;
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

package com.xmut.ebus.application.business.agent.dto;

import com.xmut.ebus.application.business.agent.support.SkillRunProfile;

/**
 * Unified GenerationRun context for SSE streaming (dry or billed skill).
 */
public final class GenerationRunContext {

    private final String runId;
    private final String userId;
    private final String holdId;
    private final String sessionId;
    private final String sceneCode;
    private final String promptText;
    private final SkillRunProfile profile;

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

    public static GenerationRunContext fromEmpty(EmptyRunContext empty, SkillRunProfile profile) {
        return new GenerationRunContext(
                empty.getRunId(),
                empty.getUserId(),
                empty.getHoldId(),
                empty.getSessionId(),
                empty.getSceneCode(),
                "empty-run",
                profile);
    }

    public static GenerationRunContext fromPicklist(PicklistRunContext picklist, SkillRunProfile profile) {
        return new GenerationRunContext(
                picklist.getRunId(),
                picklist.getUserId(),
                picklist.getHoldId(),
                picklist.getSessionId(),
                picklist.getSceneCode(),
                picklist.getPromptText(),
                profile);
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

    public SkillRunProfile getProfile() {
        return profile;
    }
}

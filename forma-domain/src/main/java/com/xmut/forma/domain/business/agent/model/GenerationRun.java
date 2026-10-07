package com.xmut.forma.domain.business.agent.model;

import com.xmut.forma.domain.business.agent.constant.GenerationRunStatus;

import java.time.Instant;

/**
 * 一次计费生成回合：关联 holdId + sessionId + 可选 artifact 引用（AD-7）。
 * <p>
 * HITL：挂起真源在 Checkpoint；本实体只存计费字段（skillId / 活跃 hold / execHold / artifactRef）。
 */
public class GenerationRun {

    private String id;
    private String userId;
    private String holdId;
    private String execHoldId;
    private String sessionId;
    private String sceneId;
    private String sceneCode;
    private String skillId;
    private String artifactRef;
    private GenerationRunStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static GenerationRun start(String id,
                                      String userId,
                                      String holdId,
                                      String sessionId,
                                      String sceneId,
                                      String sceneCode,
                                      Instant now) {
        return start(id, userId, holdId, sessionId, sceneId, sceneCode, null, now);
    }

    public static GenerationRun start(String id,
                                      String userId,
                                      String holdId,
                                      String sessionId,
                                      String sceneId,
                                      String sceneCode,
                                      String skillId,
                                      Instant now) {
        GenerationRun run = new GenerationRun();
        run.id = id;
        run.userId = userId;
        run.holdId = holdId;
        run.execHoldId = null;
        run.sessionId = sessionId;
        run.sceneId = sceneId;
        run.sceneCode = sceneCode;
        run.skillId = skillId;
        run.artifactRef = null;
        run.status = GenerationRunStatus.RUNNING;
        run.createdAt = now;
        run.updatedAt = now;
        return run;
    }

    public void markFailed(Instant now) {
        this.status = GenerationRunStatus.FAILED;
        this.updatedAt = now;
    }

    public void markSettled(String artifactRef, Instant now) {
        this.artifactRef = artifactRef;
        this.status = GenerationRunStatus.SETTLED;
        this.updatedAt = now;
    }

    /** persist 成功、settle 失败且预占已释放：对账态并清空活跃 hold。 */
    public void markNeedsReconcile(String artifactRef, Instant now) {
        markNeedsReconcile(artifactRef, now, true);
    }

    /**
     * persist 成功、settle 失败。
     * {@code clearHolds=false}：release 也失败，保留 holdId 供运维按 ACTIVE 预占对账。
     */
    public void markNeedsReconcile(String artifactRef, Instant now, boolean clearHolds) {
        this.artifactRef = artifactRef;
        this.status = GenerationRunStatus.NEEDS_RECONCILE;
        if (clearHolds) {
            this.holdId = null;
            this.execHoldId = null;
        }
        this.updatedAt = now;
    }

    /** 挂起路径 settle：落 artifactRef、清活跃 hold，status 仍 RUNNING。 */
    public void markSettledOnSuspended(String artifactRef, Instant now) {
        this.artifactRef = artifactRef;
        this.holdId = null;
        this.updatedAt = now;
    }

    /** 确认执行：绑定第二笔预占为当前活跃 hold。 */
    public void bindExecHold(String execHoldId, Instant now) {
        this.execHoldId = execHoldId;
        this.holdId = execHoldId;
        this.updatedAt = now;
    }

    public void clearExecHold(Instant now) {
        if (this.execHoldId != null && this.execHoldId.equals(this.holdId)) {
            this.holdId = null;
        }
        this.execHoldId = null;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHoldId() {
        return holdId;
    }

    public void setHoldId(String holdId) {
        this.holdId = holdId;
    }

    public String getExecHoldId() {
        return execHoldId;
    }

    public void setExecHoldId(String execHoldId) {
        this.execHoldId = execHoldId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public GenerationRunStatus getStatus() {
        return status;
    }

    public void setStatus(GenerationRunStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.xmut.ebus.domain.business.agent.model;

import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;

import java.time.Instant;

/**
 * 一次计费生成回合：关联 holdId + sessionId + 可选 artifact 引用（AD-7）。
 */
public class GenerationRun {

    private String id;
    private String userId;
    private String holdId;
    private String sessionId;
    private String sceneId;
    private String sceneCode;
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
        GenerationRun run = new GenerationRun();
        run.id = id;
        run.userId = userId;
        run.holdId = holdId;
        run.sessionId = sessionId;
        run.sceneId = sceneId;
        run.sceneCode = sceneCode;
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

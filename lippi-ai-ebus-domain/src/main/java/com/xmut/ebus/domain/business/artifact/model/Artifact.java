package com.xmut.ebus.domain.business.artifact.model;

import java.time.Instant;

/**
 * 通用成果聚合根（ArtifactStore）：类型化载荷以 {@code payloadJson} 持久化。
 */
public class Artifact {

    private String id;
    private String userId;
    private String runId;
    private ArtifactType type;
    private String sceneCode;
    private String templateId;
    private String title;
    private String payloadJson;
    private Instant createdAt;
    private Instant updatedAt;

    public static Artifact create(String id,
                                  String userId,
                                  String runId,
                                  ArtifactType type,
                                  String sceneCode,
                                  String templateId,
                                  String title,
                                  String payloadJson,
                                  Instant now) {
        Artifact artifact = new Artifact();
        artifact.id = id;
        artifact.userId = userId;
        artifact.runId = runId;
        artifact.type = type;
        artifact.sceneCode = sceneCode;
        artifact.templateId = templateId;
        artifact.title = title;
        artifact.payloadJson = payloadJson;
        artifact.createdAt = now;
        artifact.updatedAt = now;
        return artifact;
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

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public ArtifactType getType() {
        return type;
    }

    public void setType(ArtifactType type) {
        this.type = type;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
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

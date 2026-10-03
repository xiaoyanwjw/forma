package com.xmut.forma.application.business.history.dto;

import java.time.Instant;

/**
 * 历史列表条目（不含完整 view）。
 */
public class HistoryArtifactSummaryDTO {

    private String id;
    private String artifactType;
    private String sceneCode;
    private String title;
    private Instant createdAt;

    public HistoryArtifactSummaryDTO() {
    }

    public HistoryArtifactSummaryDTO(String id,
                                     String artifactType,
                                     String sceneCode,
                                     String title,
                                     Instant createdAt) {
        this.id = id;
        this.artifactType = artifactType;
        this.sceneCode = sceneCode;
        this.title = title;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.xmut.ebus.application.business.history.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 历史详情：列表字段 + Computer view（Listing 图已重签）。
 */
public class HistoryArtifactDetailDTO {

    private String id;
    private String artifactType;
    private String sceneCode;
    private String title;
    private Instant createdAt;
    private Map<String, Object> view;

    public HistoryArtifactDetailDTO() {
    }

    public HistoryArtifactDetailDTO(String id,
                                    String artifactType,
                                    String sceneCode,
                                    String title,
                                    Instant createdAt,
                                    Map<String, Object> view) {
        this.id = id;
        this.artifactType = artifactType;
        this.sceneCode = sceneCode;
        this.title = title;
        this.createdAt = createdAt;
        this.view = view;
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

    public Map<String, Object> getView() {
        return view;
    }

    public void setView(Map<String, Object> view) {
        this.view = view;
    }
}

package com.xmut.forma.domain.business.scene.model;

import com.xmut.forma.domain.business.scene.constant.SceneCategory;
import com.xmut.forma.domain.business.scene.constant.SceneStatus;

import java.time.Instant;

/**
 * 场景元数据聚合（SceneCatalog 真相；不含提示词/tool/skill 正文）。
 */
public class Scene {

    private String id;
    private String sceneCode;
    private String displayName;
    private SceneCategory category;
    private SceneStatus status;
    private int sortOrder;
    private String summary;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public SceneCategory getCategory() {
        return category;
    }

    public void setCategory(SceneCategory category) {
        this.category = category;
    }

    public SceneStatus getStatus() {
        return status;
    }

    public void setStatus(SceneStatus status) {
        this.status = status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

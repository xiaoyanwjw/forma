package com.xmut.forma.application.business.scene.dto;

/**
 * 画廊场景列表项（仅元数据；不含提示词/tool/skill 正文）。
 */
public class SceneDTO {

    private String bizId;
    private String sceneCode;
    private String displayName;
    /** 一级分类码：tech / ecommerce / content / sports / life */
    private String category;
    private String status;
    private int sortOrder;
    private String summary;

    public SceneDTO() {
    }

    public SceneDTO(String bizId, String sceneCode, String displayName, String category,
                    String status, int sortOrder, String summary) {
        this.bizId = bizId;
        this.sceneCode = sceneCode;
        this.displayName = displayName;
        this.category = category;
        this.status = status;
        this.sortOrder = sortOrder;
        this.summary = summary;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
}

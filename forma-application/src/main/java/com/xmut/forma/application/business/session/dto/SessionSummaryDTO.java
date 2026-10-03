package com.xmut.forma.application.business.session.dto;

import java.time.Instant;

/**
 * 侧栏会话列表条目。
 */
public class SessionSummaryDTO {

    private String sessionId;
    private String title;
    private String sceneCode;
    private Instant updatedAt;

    public SessionSummaryDTO() {
    }

    public SessionSummaryDTO(String sessionId, String title, String sceneCode, Instant updatedAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.sceneCode = sceneCode;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

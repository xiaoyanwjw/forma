package com.xmut.forma.domain.business.agent.model;

import java.time.Instant;

/**
 * {@code pi_session} 列表/ACL 只读投影（不含 transcript）。
 */
public final class PiSession {

    private final String sessionId;
    private final String userId;
    private final String sceneCode;
    private final String title;
    private final Instant updatedAt;

    public PiSession(String sessionId, String userId, String sceneCode, String title, Instant updatedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.sceneCode = sceneCode;
        this.title = title;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getTitle() {
        return title;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

package com.xmut.forma.domain.business.agent.model;

/**
 * {@code pi_session} 上已持久化的场景绑定（可空表示历史无场景行）。
 */
public final class SessionSceneBinding {

    private final String sessionId;
    private final String sceneId;
    private final String sceneCode;
    private final String userId;

    public SessionSceneBinding(String sessionId, String sceneId, String sceneCode) {
        this(sessionId, sceneId, sceneCode, null);
    }

    public SessionSceneBinding(String sessionId, String sceneId, String sceneCode, String userId) {
        this.sessionId = sessionId;
        this.sceneId = sceneId;
        this.sceneCode = sceneCode;
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSceneId() {
        return sceneId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getUserId() {
        return userId;
    }

    public boolean hasScene() {
        return sceneId != null && !sceneId.trim().isEmpty()
                && sceneCode != null && !sceneCode.trim().isEmpty();
    }
}

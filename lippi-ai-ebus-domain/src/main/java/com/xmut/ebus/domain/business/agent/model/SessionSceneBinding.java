package com.xmut.ebus.domain.business.agent.model;

/**
 * {@code pi_session} 上已持久化的场景绑定（可空表示历史无场景行）。
 */
public final class SessionSceneBinding {

    private final String sessionId;
    private final String sceneId;
    private final String sceneCode;

    public SessionSceneBinding(String sessionId, String sceneId, String sceneCode) {
        this.sessionId = sessionId;
        this.sceneId = sceneId;
        this.sceneCode = sceneCode;
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

    public boolean hasScene() {
        return sceneId != null && !sceneId.trim().isEmpty()
                && sceneCode != null && !sceneCode.trim().isEmpty();
    }
}

package com.xmut.ebus.domain.business.agent.repository;

import com.xmut.ebus.domain.business.agent.model.SessionSceneBinding;

import java.util.Optional;

/**
 * {@code pi_session} 场景列读写（AD-15；与 SessionStore transcript 端口配合）。
 */
public interface PiSessionSceneRepository {

    /**
     * 查会话场景；会话不存在则 empty。
     */
    Optional<SessionSceneBinding> findBySessionId(String sessionId);

    /**
     * 无则建空会话并写入场景；有则只更新场景列（调用方已做冲突校验）。
     */
    void ensureBound(String sessionId, String sceneId, String sceneCode);
}

package com.xmut.ebus.infrastructure.persistence.repository.business.agent;

import com.xmut.ebus.domain.business.agent.model.SessionSceneBinding;
import com.xmut.ebus.domain.business.agent.repository.PiSessionSceneRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code pi_session} 场景列读写；与 {@link com.xmut.ebus.infrastructure.session.MysqlSessionStore} 共用表。
 */
@Repository
@RequiredArgsConstructor
public class PiSessionSceneRepositoryImpl implements PiSessionSceneRepository {

    private final PiSessionMapper sessionMapper;

    @Override
    public Optional<SessionSceneBinding> findBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        PiSessionPO row = sessionMapper.selectById(sessionId.trim());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionSceneBinding(
                row.getSessionId(), row.getSceneId(), row.getSceneCode(), row.getUserId()));
    }

    @Override
    public void ensureBound(String sessionId, String sceneId, String sceneCode, String userId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        if (!StringUtils.hasText(sceneId) || !StringUtils.hasText(sceneCode)) {
            throw new IllegalArgumentException("scene required");
        }
        String id = sessionId.trim();
        String sid = sceneId.trim();
        String code = sceneCode.trim();
        String uid = StringUtils.hasText(userId) ? userId.trim() : null;
        Instant now = Instant.now();

        PiSessionPO existing = sessionMapper.selectById(id);
        if (existing == null) {
            PiSessionPO created = new PiSessionPO();
            created.setSessionId(id);
            created.setUserId(uid);
            created.setSceneId(sid);
            created.setSceneCode(code);
            created.setTitle(null);
            created.setSource("api");
            created.setStatus("active");
            created.setParentSessionId(null);
            created.setCompactAnchorSeq(0L);
            created.setLastRunId(null);
            created.setMessageCount(0);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            try {
                sessionMapper.insert(created);
            } catch (DuplicateKeyException e) {
                updateSceneIfCompatible(id, sid, code, now);
                fillUserIdIfAbsent(id, uid);
            }
            return;
        }
        updateSceneIfCompatible(id, sid, code, now);
        fillUserIdIfAbsent(id, uid);
    }

    private void fillUserIdIfAbsent(String sessionId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        sessionMapper.updateUserIdIfNull(sessionId, userId);
    }

    /**
     * 写前再读：已绑且与请求不同则失败（防并发 last-write-win）；同场景或未绑才 update。
     */
    private void updateSceneIfCompatible(String sessionId, String sceneId, String sceneCode, Instant now) {
        PiSessionPO row = sessionMapper.selectById(sessionId);
        if (row == null) {
            throw new IllegalStateException("session missing after bind race: " + sessionId);
        }
        SessionSceneBinding bound = new SessionSceneBinding(
                row.getSessionId(), row.getSceneId(), row.getSceneCode(), row.getUserId());
        if (bound.hasScene()) {
            if (!sceneId.equals(bound.getSceneId()) || !sceneCode.equals(bound.getSceneCode())) {
                throw new IllegalStateException("session already bound to another scene");
            }
            return;
        }
        sessionMapper.updateScene(sessionId, sceneId, sceneCode, now);
    }
}

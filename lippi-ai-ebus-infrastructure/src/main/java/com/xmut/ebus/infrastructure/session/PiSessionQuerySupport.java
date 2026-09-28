package com.xmut.ebus.infrastructure.session;

import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
import com.xmut.ebus.domain.business.agent.repository.PiSessionQueryRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@code pi_session} 只读查询适配器（不污染 SessionStore 写端口）。
 */
@Repository
@RequiredArgsConstructor
public class PiSessionQuerySupport implements PiSessionQueryRepository {

    private final PiSessionMapper sessionMapper;

    @Override
    public List<PiSessionMeta> selectByUserSince(String userId, Instant since, String sceneCodeOrNull, int limit) {
        if (!StringUtils.hasText(userId) || since == null || limit < 1) {
            return Collections.emptyList();
        }
        String sceneCode = StringUtils.hasText(sceneCodeOrNull) ? sceneCodeOrNull.trim() : null;
        List<PiSessionPO> rows = sessionMapper.selectByUserSince(userId.trim(), since, sceneCode, limit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PiSessionMeta> out = new ArrayList<PiSessionMeta>(rows.size());
        for (PiSessionPO row : rows) {
            if (row != null) {
                out.add(toMeta(row));
            }
        }
        return out;
    }

    @Override
    public Optional<PiSessionMeta> findBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        PiSessionPO row = sessionMapper.selectById(sessionId.trim());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toMeta(row));
    }

    private static PiSessionMeta toMeta(PiSessionPO row) {
        return new PiSessionMeta(
                row.getSessionId(),
                row.getUserId(),
                row.getSceneCode(),
                row.getTitle(),
                row.getUpdatedAt());
    }
}

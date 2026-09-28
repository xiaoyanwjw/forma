package com.xmut.ebus.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
import com.xmut.ebus.domain.business.agent.model.PiSessionMeta;
import com.xmut.ebus.domain.business.agent.repository.PiSessionQueryRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionEntryMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionEntryPO;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionPO;
import com.xmut.lims.pi.ai.message.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@code pi_session} / {@code pi_session_entry} 只读查询适配器（不污染 SessionStore 写端口）。
 */
@Repository
@RequiredArgsConstructor
public class PiSessionQuerySupport implements PiSessionQueryRepository {

    private static final int RAW_BATCH_MIN = 50;

    private final PiSessionMapper sessionMapper;
    private final PiSessionEntryMapper entryMapper;
    private final ObjectMapper objectMapper;

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

    @Override
    public Page<PiMessageDTO> getMessageList(String sessionId, String nextToken, int limit) {
        if (!StringUtils.hasText(sessionId) || limit < 1) {
            return Page.empty();
        }
        String sid = sessionId.trim();
        PiSessionPO session = sessionMapper.selectById(sid);
        if (session == null) {
            return Page.empty();
        }
        Long cursor = parseToken(nextToken);
        if (StringUtils.hasText(nextToken) && cursor == null) {
            return Page.empty();
        }
        long anchor = session.getCompactAnchorSeq();
        int need = limit + 1;
        int batchSize = Math.max(limit * 2, RAW_BATCH_MIN);
        List<PiMessageDTO> newestFirst = new ArrayList<PiMessageDTO>(need);
        boolean exhausted = false;
        while (newestFirst.size() < need && !exhausted) {
            List<PiSessionEntryPO> batch = entryMapper.selectPageDesc(sid, anchor, cursor, batchSize);
            if (batch == null || batch.isEmpty()) {
                exhausted = true;
                break;
            }
            for (PiSessionEntryPO row : batch) {
                if (row == null) {
                    continue;
                }
                cursor = Long.valueOf(row.getSeq());
                PiMessageDTO dto = toReplayMessage(row);
                if (dto == null) {
                    continue;
                }
                newestFirst.add(dto);
                if (newestFirst.size() >= need) {
                    break;
                }
            }
            if (batch.size() < batchSize) {
                exhausted = true;
            }
        }
        boolean hasMore = newestFirst.size() > limit;
        if (hasMore) {
            newestFirst = new ArrayList<PiMessageDTO>(newestFirst.subList(0, limit));
        }
        Collections.reverse(newestFirst);
        String token = null;
        if (hasMore && !newestFirst.isEmpty()) {
            token = String.valueOf(newestFirst.get(0).getSeq());
        }
        return Page.of(newestFirst, token);
    }

    private static Long parseToken(String nextToken) {
        if (!StringUtils.hasText(nextToken)) {
            return null;
        }
        try {
            return Long.valueOf(nextToken.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PiMessageDTO toReplayMessage(PiSessionEntryPO row) {
        if (row == null || !StringUtils.hasText(row.getPayload())) {
            return null;
        }
        Message message;
        try {
            message = MessagePayloadCodec.fromPayload(objectMapper, row.getPayload());
        } catch (RuntimeException ex) {
            return null;
        }
        if (message == null || !StringUtils.hasText(message.getRole())) {
            return null;
        }
        String role = message.getRole();
        if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
            return null;
        }
        if (!StringUtils.hasText(message.getContent())) {
            return null;
        }
        return new PiMessageDTO(
                role,
                message.getContent().trim(),
                row.getCreatedAt(),
                row.getSeq());
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

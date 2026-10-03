package com.xmut.forma.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.common.page.Page;
import com.xmut.forma.domain.business.agent.model.PiLogicalRunRef;
import com.xmut.forma.domain.business.agent.model.PiMessage;
import com.xmut.forma.domain.business.agent.model.PiSession;
import com.xmut.forma.domain.business.agent.model.PiToolCallRef;
import com.xmut.forma.domain.business.agent.repository.PiSessionQueryRepository;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiSessionEntryMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiLogicalRunRow;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiSessionEntryPO;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiSessionPO;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
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
public class PiSessionQueryRepositoryImpl implements PiSessionQueryRepository {

    private final PiSessionMapper sessionMapper;
    private final PiSessionEntryMapper entryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<PiSession> selectByUserSince(String userId, Instant since, String sceneCodeOrNull, int limit) {
        if (!StringUtils.hasText(userId) || since == null || limit < 1) {
            return Collections.emptyList();
        }
        String sceneCode = StringUtils.hasText(sceneCodeOrNull) ? sceneCodeOrNull.trim() : null;
        List<PiSessionPO> rows = sessionMapper.selectByUserSince(userId.trim(), since, sceneCode, limit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PiSession> out = new ArrayList<PiSession>(rows.size());
        for (PiSessionPO row : rows) {
            if (row != null) {
                out.add(toSession(row));
            }
        }
        return out;
    }

    @Override
    public Optional<PiSession> findBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        PiSessionPO row = sessionMapper.selectById(sessionId.trim());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toSession(row));
    }

    @Override
    public Page<PiLogicalRunRef> getLogicalRunIds(String sessionId, String nextToken, int limit) {
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
        int need = limit + 1;
        List<PiLogicalRunRow> rows = entryMapper.selectLogicalRunPage(
                sid, session.getCompactAnchorSeq(), cursor, need);
        if (rows == null || rows.isEmpty()) {
            return Page.empty();
        }
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<PiLogicalRunRow>(rows.subList(0, limit));
        }
        List<PiLogicalRunRef> items = new ArrayList<PiLogicalRunRef>(rows.size());
        for (PiLogicalRunRow row : rows) {
            if (row == null || !StringUtils.hasText(row.getLogicalRunId())) {
                continue;
            }
            items.add(new PiLogicalRunRef(row.getLogicalRunId().trim(), row.getTipSeq()));
        }
        String token = null;
        if (hasMore && !items.isEmpty()) {
            token = String.valueOf(items.get(items.size() - 1).getTipSeq());
        }
        return Page.of(items, token);
    }

    @Override
    public List<PiMessage> getMessagesByLogicalRunIds(String sessionId, List<String> logicalRunIds) {
        if (!StringUtils.hasText(sessionId) || logicalRunIds == null || logicalRunIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sid = sessionId.trim();
        PiSessionPO session = sessionMapper.selectById(sid);
        if (session == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<String>();
        for (String id : logicalRunIds) {
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<PiSessionEntryPO> rows = entryMapper.selectByLogicalRunIds(
                sid, session.getCompactAnchorSeq(), ids);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PiMessage> out = new ArrayList<PiMessage>();
        for (PiSessionEntryPO row : rows) {
            PiMessage dto = toReplayMessage(row);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
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

    private PiMessage toReplayMessage(PiSessionEntryPO row) {
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
        List<PiToolCallRef> toolCalls = toToolCallRefs(message);
        boolean hasContent = StringUtils.hasText(message.getContent());
        if ("user".equalsIgnoreCase(role)) {
            if (!hasContent) {
                return null;
            }
            return new PiMessage(
                    role,
                    message.getContent().trim(),
                    row.getCreatedAt(),
                    row.getSeq(),
                    null,
                    Collections.<PiToolCallRef>emptyList(),
                    row.getRunId());
        }
        if ("assistant".equalsIgnoreCase(role)) {
            if (!hasContent && toolCalls.isEmpty()) {
                return null;
            }
            return new PiMessage(
                    role,
                    hasContent ? message.getContent().trim() : "",
                    row.getCreatedAt(),
                    row.getSeq(),
                    null,
                    toolCalls,
                    row.getRunId());
        }
        if ("tool".equalsIgnoreCase(role)) {
            return new PiMessage(
                    role,
                    hasContent ? message.getContent().trim() : "",
                    row.getCreatedAt(),
                    row.getSeq(),
                    message.getToolCallId(),
                    Collections.<PiToolCallRef>emptyList(),
                    row.getRunId());
        }
        return null;
    }

    private static List<PiToolCallRef> toToolCallRefs(Message message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return Collections.emptyList();
        }
        List<PiToolCallRef> out = new ArrayList<PiToolCallRef>();
        for (ToolCallEntry tc : message.getToolCalls()) {
            if (tc == null || !StringUtils.hasText(tc.getToolName())) {
                continue;
            }
            out.add(new PiToolCallRef(tc.getId(), tc.getToolName().trim()));
        }
        return out;
    }

    private static PiSession toSession(PiSessionPO row) {
        return new PiSession(
                row.getSessionId(),
                row.getUserId(),
                row.getSceneCode(),
                row.getTitle(),
                row.getUpdatedAt());
    }
}

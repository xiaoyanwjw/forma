package com.xmut.ebus.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionEntryMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionEntryPO;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiSessionPO;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.session.Session;
import com.xmut.lims.pi.agent.session.SessionStore;
import com.xmut.lims.pi.agent.session.SessionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adam 生产 SessionStore（MySQL）。
 * 功能描述：用 pi_session / pi_session_entry 实现 Pi Message 投影端口。
 * 关键设计：行为对齐 InMemory/Sqlite；Entry 为适配器私有编码；写路径对会话行 FOR UPDATE。
 */
@Component
@Primary
@RequiredArgsConstructor
public class MysqlSessionStore implements SessionStore {

    private static final int LIST_LIMIT_MIN = 1;
    private static final int LIST_LIMIT_MAX = 200;
    private static final String ENTRY_TYPE_MESSAGE = "message";

    private final PiSessionMapper sessionMapper;
    private final PiSessionEntryMapper entryMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        if (!StringUtils.hasText(session.getSessionId())) {
            throw new IllegalArgumentException("session.sessionId required");
        }
        String sessionId = requireSessionId(session.getSessionId());
        Instant now = Instant.now();
        ensureSessionRow(sessionId,
                StringUtils.hasText(session.getSource()) ? session.getSource() : "api",
                session.getCreatedAt(),
                session.getUpdatedAt() != null ? session.getUpdatedAt() : now);

        entryMapper.deleteBySessionId(sessionId);
        long nextSeq = 1L;
        List<Message> msgs = session.getMessages();
        if (msgs != null) {
            Instant created = now;
            for (Message m : msgs) {
                if (m == null || isSystem(m)) {
                    continue;
                }
                insertEntry(sessionId, nextSeq++, null, m, created);
            }
        }
        PiSessionPO meta = new PiSessionPO();
        meta.setSessionId(sessionId);
        meta.setSource(StringUtils.hasText(session.getSource()) ? session.getSource() : null);
        meta.setCompactAnchorSeq(session.getCompactAnchorSeq());
        meta.setLastRunId(session.getLastRunId());
        meta.setMessageCount((int) (nextSeq - 1L));
        meta.setUpdatedAt(session.getUpdatedAt() != null ? session.getUpdatedAt() : now);
        sessionMapper.updateMeta(meta);
        return find(sessionId).orElseThrow(() -> new IllegalStateException("save lost session"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> find(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        PiSessionPO row = sessionMapper.selectById(sessionId.trim());
        if (row == null) {
            return Optional.empty();
        }
        List<Message> projected = getMessages(row.getSessionId(), row.getCompactAnchorSeq());
        return Optional.of(toSession(row, projected));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String id = sessionId.trim();
        entryMapper.deleteBySessionId(id);
        sessionMapper.deleteById(id);
    }

    /**
     * 无则插入空会话（anchor=0）；有则返回投影。唯一键冲突按「再查返回」处理并发建号。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Session getOrCreate(Session.Meta meta) {
        Objects.requireNonNull(meta, "meta");
        String sessionId = StringUtils.hasText(meta.getSessionId())
                ? requireSessionId(meta.getSessionId())
                : UUID.randomUUID().toString();
        PiSessionPO existing = sessionMapper.selectById(sessionId);
        if (Objects.nonNull(existing)) {
            return toSession(existing, getMessages(sessionId, existing.getCompactAnchorSeq()));
        }

        Instant now = Instant.now();
        String source = StringUtils.hasText(meta.getSource()) ? meta.getSource().trim() : "api";
        PiSessionPO session = new PiSessionPO();
        session.setSessionId(sessionId);
        session.setUserId(null);
        session.setTitle(meta.getTitle());
        session.setSource(source);
        session.setStatus("active");
        session.setParentSessionId(meta.getParentSessionId());
        session.setCompactAnchorSeq(0L);
        session.setLastRunId(null);
        session.setMessageCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        try {
            sessionMapper.insert(session);
        } catch (DuplicateKeyException e) {
            PiSessionPO raced = sessionMapper.selectById(sessionId);
            if (raced == null) {
                throw new IllegalStateException("getOrCreate unique race but row missing", e);
            }
            return toSession(raced, getMessages(sessionId, raced.getCompactAnchorSeq()));
        }
        return toSession(session, Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> load(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Collections.emptyList();
        }
        PiSessionPO session = sessionMapper.selectById(sessionId.trim());
        if (session == null) {
            return Collections.emptyList();
        }

        return getMessages(session.getSessionId(), session.getCompactAnchorSeq());
    }

    /**
     * 追加本 turn：行锁 → 同 runId 整批跳过 → 丢弃 system → 单调 seq → 刷新 meta。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void append(String sessionId, String runId, List<Message> messages) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }

        String id = requireSessionId(sessionId);
        // 多 Pod：seq 分配与 runId 幂等检查必须串行
        if (sessionMapper.selectByIdForUpdate(id) == null) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        if (StringUtils.hasText(runId) && entryMapper.countBySessionAndRunId(id, runId) > 0) {
            return;
        }
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long nextSeq = nextSeq(id);
        Instant now = Instant.now();
        boolean any = false;
        for (Message m : messages) {
            if (m == null || isSystem(m)) {
                continue;
            }
            insertEntry(id, nextSeq++, runId, m, now);
            any = true;
        }
        if (any) {
            int count = entryMapper.countBySessionId(id);
            sessionMapper.updateAfterAppend(id, runId, count, now);
        }
    }

    /**
     * Compact = 只更新 {@code compact_anchor_seq}，不 DELETE entry。
     * 同 seq 幂等；非 system summary 追加一条 {@code compact-seq-next} runId（计入幂等集）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCompactAnchor(String sessionId, long seq, Message summaryMessage) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        String id = requireSessionId(sessionId);
        PiSessionPO row = sessionMapper.selectByIdForUpdate(id);
        if (row == null) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        long next = nextSeq(id);
        if (seq < 0L || seq >= next) {
            throw new IllegalArgumentException(
                    "compact anchor seq out of range: " + seq + " (nextSeq=" + next + ")");
        }
        if (row.getCompactAnchorSeq() == seq) {
            return;
        }
        Instant now = Instant.now();
        sessionMapper.updateCompactAnchor(id, seq, now);
        if (summaryMessage != null && !isSystem(summaryMessage)) {
            String compactRunId = "compact-" + seq + "-" + next;
            insertEntry(id, next, compactRunId, summaryMessage, now);
            int count = entryMapper.countBySessionId(id);
            sessionMapper.updateMessageCount(id, count, now);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummary> listRecent(int limit) {
        int clamped = clampLimit(limit);
        List<PiSessionPO> rows = sessionMapper.selectRecent(clamped);
        return toSummaries(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummary> listChildren(String parentSessionId) {
        List<PiSessionPO> rows;
        if (!StringUtils.hasText(parentSessionId)) {
            rows = sessionMapper.selectChildrenRoots();
        } else {
            rows = sessionMapper.selectChildrenByParent(parentSessionId.trim());
        }
        return toSummaries(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionSummary> findSummary(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        PiSessionPO row = sessionMapper.selectById(sessionId.trim());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toSummary(row));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(String sessionId, String title) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String id = requireSessionId(sessionId);
        if (sessionMapper.selectById(id) == null) {
            return;
        }
        Instant now = Instant.now();
        String normalized = StringUtils.hasText(title) ? title.trim() : null;
        sessionMapper.updateTitle(id, normalized, now);
    }

    /** 对齐 InMemory：sessionId 禁止 ':'。 */
    private static String requireSessionId(String sessionId) {
        String id = sessionId.trim();
        if (id.indexOf(':') >= 0) {
            throw new IllegalArgumentException("sessionId must not contain ':'");
        }
        return id;
    }

    private void ensureSessionRow(String sessionId, String source,
                                  Instant createdAt, Instant updatedAt) {
        if (sessionMapper.selectById(sessionId) != null) {
            return;
        }
        Instant c = createdAt != null ? createdAt : Instant.now();
        Instant u = updatedAt != null ? updatedAt : c;
        PiSessionPO created = new PiSessionPO();
        created.setSessionId(sessionId);
        created.setSource(source != null ? source : "api");
        created.setStatus("active");
        created.setCompactAnchorSeq(0L);
        created.setMessageCount(0);
        created.setCreatedAt(c);
        created.setUpdatedAt(u);
        try {
            sessionMapper.insert(created);
        } catch (DuplicateKeyException ignored) {
            // 并发创建：后续 updateMeta 覆盖即可
        }
    }

    private void insertEntry(String sessionId, long seq, String runId,
                             Message message, Instant createdAt) {
        PiSessionEntryPO entry = new PiSessionEntryPO();
        entry.setId(UUID.randomUUID().toString());
        entry.setSessionId(sessionId);
        entry.setSeq(seq);
        entry.setEntryType(ENTRY_TYPE_MESSAGE);
        entry.setParentId(null);
        entry.setRunId(runId);
        entry.setPayload(MessagePayloadCodec.toPayload(objectMapper, message));
        entry.setCreatedAt(createdAt);
        entryMapper.insert(entry);
    }

    private long nextSeq(String sessionId) {
        Long next = entryMapper.selectNextSeq(sessionId);
        return next == null ? 1L : next;
    }

    /** 投影：SQL 已滤 {@code seq > anchor}；此处再丢弃 system（与 InMemory 一致）。 */
    private List<Message> getMessages(String sessionId, long anchor) {
        List<PiSessionEntryPO> rows = entryMapper.selectProjected(sessionId, anchor);
        List<Message> out = new ArrayList<>();
        for (PiSessionEntryPO row : rows) {
            Message m = MessagePayloadCodec.fromPayload(objectMapper, row.getPayload());
            if (isSystem(m)) {
                continue;
            }
            out.add(m);
        }
        return Collections.unmodifiableList(out);
    }

    private static Session toSession(PiSessionPO row, List<Message> projected) {
        return Session.builder()
                .sessionId(row.getSessionId())
                .title(row.getTitle())
                .source(row.getSource())
                .parentSessionId(row.getParentSessionId())
                .compactAnchorSeq(row.getCompactAnchorSeq())
                .lastRunId(row.getLastRunId())
                .messageCount(row.getMessageCount())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .messages(projected)
                .build();
    }

    private static List<SessionSummary> toSummaries(List<PiSessionPO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<SessionSummary> out = new ArrayList<>(rows.size());
        for (PiSessionPO row : rows) {
            out.add(toSummary(row));
        }
        return Collections.unmodifiableList(out);
    }

    private static SessionSummary toSummary(PiSessionPO row) {
        return SessionSummary.builder()
                .sessionId(row.getSessionId())
                .parentSessionId(row.getParentSessionId())
                .title(row.getTitle())
                .source(row.getSource())
                .status(row.getStatus())
                .messageCount(row.getMessageCount())
                .updatedAt(row.getUpdatedAt())
                .lastRunId(row.getLastRunId())
                .build();
    }

    private static boolean isSystem(Message m) {
        return m != null && "system".equalsIgnoreCase(m.getRole());
    }

    private static int clampLimit(int limit) {
        if (limit < LIST_LIMIT_MIN) {
            return LIST_LIMIT_MIN;
        }
        return Math.min(limit, LIST_LIMIT_MAX);
    }
}

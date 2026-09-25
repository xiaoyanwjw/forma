package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.ai.message.Message;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 同进程内存 SessionStore：与 {@link SqliteSessionStore} / 未来 MySQL 同端口语义。
 *
 * <p>本模块 MissingBean 过渡默认（AD-S8）；<b>不是</b>跨进程 / 生产真相。
 * Adam 生产默认 MySQL → ebus-infrastructure {@code MysqlSessionStore}。键 = {@code sessionId}（禁止 {@code ':'}）；独立于 Checkpoint。
 */
public final class InMemorySessionStore implements SessionStore {

    private static final int LIST_LIMIT_MIN = 1;
    private static final int LIST_LIMIT_MAX = 200;

    private final Map<String, Entry> byKey = new ConcurrentHashMap<>();

    @Override
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        if (!StringUtils.hasText(session.getSessionId())) {
            throw new IllegalArgumentException("session.sessionId required");
        }
        String k = key(session.getSessionId());
        synchronized (byKey) {
            Entry entry = byKey.computeIfAbsent(k, ignored -> new Entry(session.getSessionId()));
            synchronized (entry) {
                entry.applyCompatSave(session);
                return entry.toSession();
            }
        }
    }

    @Override
    public Optional<Session> find(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        synchronized (entry) {
            return Optional.of(entry.toSession());
        }
    }

    @Override
    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String k = sessionId.trim();
        Entry entry = byKey.get(k);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            byKey.remove(k, entry);
        }
    }

    @Override
    public Session getOrCreate(Session.Meta meta) {
        Objects.requireNonNull(meta, "meta");
        String sessionId = StringUtils.hasText(meta.getSessionId())
                ? meta.getSessionId().trim()
                : UUID.randomUUID().toString();
        String k = key(sessionId);
        Entry created = new Entry(sessionId);
        created.source = StringUtils.hasText(meta.getSource()) ? meta.getSource().trim() : "api";
        created.parentSessionId = meta.getParentSessionId();
        created.title = meta.getTitle();
        Instant now = Instant.now();
        created.createdAt = now;
        created.updatedAt = now;

        Entry existing = byKey.putIfAbsent(k, created);
        Entry entry = existing != null ? existing : created;
        synchronized (entry) {
            return entry.toSession();
        }
    }

    @Override
    public List<Message> load(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Collections.emptyList();
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            return Collections.emptyList();
        }
        synchronized (entry) {
            return entry.projectMessages();
        }
    }

    @Override
    public void append(String sessionId, String runId, List<Message> messages) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        synchronized (entry) {
            entry.append(runId, messages);
        }
    }

    @Override
    public void setCompactAnchor(String sessionId, long seq, Message summaryMessage) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        synchronized (entry) {
            entry.setCompactAnchor(seq, summaryMessage);
        }
    }

    @Override
    public List<SessionSummary> listRecent(int limit) {
        int clamped = clampLimit(limit);
        List<Entry> snapshot = new ArrayList<>(byKey.values());
        snapshot.sort(Comparator.comparing(Entry::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return snapshot.stream()
                .limit(clamped)
                .map(Entry::toSummary)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    @Override
    public List<SessionSummary> listChildren(String parentSessionId) {
        boolean roots = !StringUtils.hasText(parentSessionId);
        String parent = roots ? null : parentSessionId.trim();
        return byKey.values().stream()
                .filter(entry -> roots
                        ? entry.parentSessionId == null
                        : Objects.equals(parent, entry.parentSessionId))
                .sorted(Comparator.comparing(Entry::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(Entry::toSummary)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    @Override
    public Optional<SessionSummary> findSummary(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        synchronized (entry) {
            return Optional.of(entry.toSummary());
        }
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        Entry entry = byKey.get(sessionId.trim());
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.title = StringUtils.hasText(title) ? title.trim() : null;
            entry.updatedAt = Instant.now();
        }
    }

    private static int clampLimit(int limit) {
        if (limit < LIST_LIMIT_MIN) {
            return LIST_LIMIT_MIN;
        }
        return Math.min(limit, LIST_LIMIT_MAX);
    }

    private static String key(String sessionId) {
        if (sessionId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("sessionId must not contain ':'");
        }
        return sessionId;
    }

    private static final class Row {
        final long seq;
        final String runId;
        final Message message;

        Row(long seq, String runId, Message message) {
            this.seq = seq;
            this.runId = runId;
            this.message = message;
        }
    }

    private static final class Entry {
        final String sessionId;
        String source = "api";
        String status = "active";
        String parentSessionId;
        String title;
        Instant createdAt;
        Instant updatedAt;
        long compactAnchorSeq;
        String lastRunId;
        long nextSeq = 1L;
        final List<Row> rows = new ArrayList<>();
        final Set<String> appendedRunIds = new HashSet<>();

        Entry(String sessionId) {
            this.sessionId = sessionId;
            Instant now = Instant.now();
            this.createdAt = now;
            this.updatedAt = now;
        }

        void applyCompatSave(Session session) {
            if (StringUtils.hasText(session.getSource())) {
                this.source = session.getSource();
            }
            this.compactAnchorSeq = session.getCompactAnchorSeq();
            this.lastRunId = session.getLastRunId();
            if (session.getCreatedAt() != null) {
                this.createdAt = session.getCreatedAt();
            }
            this.updatedAt = session.getUpdatedAt() != null ? session.getUpdatedAt() : Instant.now();
            this.rows.clear();
            this.appendedRunIds.clear();
            this.nextSeq = 1L;
            List<Message> msgs = session.getMessages();
            if (msgs != null) {
                for (Message m : msgs) {
                    if (m == null || isSystem(m)) {
                        continue;
                    }
                    this.rows.add(new Row(this.nextSeq++, null, m));
                }
            }
        }

        void append(String runId, List<Message> messages) {
            if (StringUtils.hasText(runId) && this.appendedRunIds.contains(runId)) {
                return;
            }
            if (messages == null || messages.isEmpty()) {
                return;
            }
            boolean any = false;
            for (Message m : messages) {
                if (m == null || isSystem(m)) {
                    continue;
                }
                this.rows.add(new Row(this.nextSeq++, runId, m));
                any = true;
            }
            if (any) {
                if (StringUtils.hasText(runId)) {
                    this.appendedRunIds.add(runId);
                }
                this.lastRunId = runId;
                this.updatedAt = Instant.now();
            }
        }

        void setCompactAnchor(long seq, Message summaryMessage) {
            if (seq < 0L || seq >= this.nextSeq) {
                throw new IllegalArgumentException(
                        "compact anchor seq out of range: " + seq + " (nextSeq=" + this.nextSeq + ")");
            }
            if (seq == this.compactAnchorSeq) {
                return;
            }
            this.compactAnchorSeq = seq;
            this.updatedAt = Instant.now();
            if (summaryMessage != null && !isSystem(summaryMessage)) {
                String compactRunId = "compact-" + seq + "-" + this.nextSeq;
                this.rows.add(new Row(this.nextSeq++, compactRunId, summaryMessage));
                this.appendedRunIds.add(compactRunId);
            }
        }

        List<Message> projectMessages() {
            List<Message> out = new ArrayList<>();
            for (Row row : this.rows) {
                if (row.seq <= this.compactAnchorSeq) {
                    continue;
                }
                if (isSystem(row.message)) {
                    continue;
                }
                out.add(row.message);
            }
            return Collections.unmodifiableList(out);
        }

        Instant getUpdatedAt() {
            return this.updatedAt;
        }

        Session toSession() {
            List<Message> projected = projectMessages();
            return Session.builder()
                    .sessionId(this.sessionId)
                    .messages(projected)
                    .createdAt(this.createdAt)
                    .updatedAt(this.updatedAt)
                    .compactAnchorSeq(this.compactAnchorSeq)
                    .lastRunId(this.lastRunId)
                    .messageCount(this.rows.size())
                    .source(this.source)
                    .parentSessionId(this.parentSessionId)
                    .title(this.title)
                    .build();
        }

        SessionSummary toSummary() {
            return SessionSummary.builder()
                    .sessionId(this.sessionId)
                    .parentSessionId(this.parentSessionId)
                    .title(this.title)
                    .source(this.source)
                    .status(this.status)
                    .messageCount(this.rows.size())
                    .updatedAt(this.updatedAt)
                    .lastRunId(this.lastRunId)
                    .build();
        }

        private static boolean isSystem(Message m) {
            return m != null && "system".equalsIgnoreCase(m.getRole());
        }
    }

    /** Spring {@code destroyMethod=close} 兼容；内存无资源可释。 */
    public void close() {
        // no-op
    }
}

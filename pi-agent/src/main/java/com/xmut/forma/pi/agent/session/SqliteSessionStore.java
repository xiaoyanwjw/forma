package com.xmut.forma.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.message.ContentPart;
import com.xmut.forma.pi.ai.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 本地 SQLite SessionStore。
 * 功能描述：把会话 transcript 落到显式配置的 sqlite 文件。
 * 关键设计：非生产默认（AD-S8）；禁止在空路径下静默落 {cwd}/.lippi-pi/state.db。
 */
public final class SqliteSessionStore implements SessionStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqliteSessionStore.class);
    private static final String SCHEMA_RESOURCE = "pi/session/schema.sql";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Pi SessionStore schema 版本；旧库不做迁移。 */
    public static final int SCHEMA_USER_VERSION = 1;

    private final Path dbPath;
    private final Connection connection;
    private final Object lock = new Object();

    public SqliteSessionStore(Path sqlitePath) {
        this.dbPath = Objects.requireNonNull(sqlitePath, "sqlitePath").toAbsolutePath().normalize();
        Connection opened = null;
        boolean success = false;
        try {
            Path parent = this.dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            opened = DriverManager.getConnection("jdbc:sqlite:" + this.dbPath);
            // 事务由 BEGIN IMMEDIATE / COMMIT / ROLLBACK 显式管理
            opened.setAutoCommit(true);
            applyPragmas(opened);
            rejectLegacySchema(opened, this.dbPath);
            applySchema(opened);
            setUserVersion(opened, SCHEMA_USER_VERSION);
            success = true;
        } catch (ClassNotFoundException | IOException | SQLException e) {
            throw new IllegalStateException("failed to open SqliteSessionStore at " + this.dbPath, e);
        } finally {
            if (!success && opened != null) {
                try {
                    opened.close();
                } catch (SQLException ignored) {
                    // best-effort：构造失败时释放文件锁
                }
            }
        }
        this.connection = opened;
    }

    public SqliteSessionStore(String sqlitePath) {
        this(Paths.get(Objects.requireNonNull(sqlitePath, "sqlitePath")));
    }

    /** 解析配置路径：空 → cwd 默认；相对 → 相对 cwd；绝对 → 原样。 */
    public static Path resolveSqlitePath(String configured) {
        if (!StringUtils.hasText(configured)) {
            return Paths.get(System.getProperty("user.dir"), ".lippi-pi", "state.db")
                    .toAbsolutePath()
                    .normalize();
        }
        Path p = Paths.get(configured.trim());
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p);
        }
        return p.toAbsolutePath().normalize();
    }

    public Path getDbPath() {
        return dbPath;
    }

    private static void applyPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
    }

    private static void rejectLegacySchema(Connection conn, Path dbPath) throws SQLException {
        if (!tableExists(conn, "pi_session")) {
            return;
        }
        int userVersion = readUserVersion(conn);
        boolean hasTenantId = hasColumn(conn, "pi_session", "tenant_id");
        if (hasTenantId || userVersion < SCHEMA_USER_VERSION) {
            throw new IllegalStateException(
                    "旧会话库不兼容（" + dbPath + "）。请删除旧会话库后重试");
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int readUserVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            if (!rs.next()) {
                return 0;
            }
            return rs.getInt(1);
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (column.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setUserVersion(Connection conn, int version) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA user_version = " + version);
        }
    }

    private static void applySchema(Connection conn) throws IOException, SQLException {
        String ddl = readClasspathResource(SCHEMA_RESOURCE);
        try (Statement st = conn.createStatement()) {
            for (String raw : ddl.split(";")) {
                String statement = stripSqlComments(raw);
                if (!statement.isEmpty()) {
                    st.execute(statement);
                }
            }
        }
    }

    /** 去掉行注释后返回可执行 SQL；全注释块返回空串。 */
    static String stripSqlComments(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(line);
        }
        return cleaned.toString().trim();
    }

    private static String readClasspathResource(String name) throws IOException {
        InputStream in = SqliteSessionStore.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException("classpath resource missing: " + name);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    @Override
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        if (!StringUtils.hasText(session.getSessionId())) {
            throw new IllegalArgumentException("session.sessionId required");
        }
        synchronized (lock) {
            try {
                beginImmediate();
                ensureSessionRow(session.getSessionId(),
                        StringUtils.hasText(session.getSource()) ? session.getSource() : "api",
                        session.getCreatedAt(),
                        session.getUpdatedAt());
                deleteMessages(session.getSessionId());
                long nextSeq = 1L;
                List<Message> msgs = session.getMessages();
                if (msgs != null) {
                    Instant now = Instant.now();
                    for (Message m : msgs) {
                        if (m == null || isSystem(m)) {
                            continue;
                        }
                        insertMessage(session.getSessionId(), nextSeq++, null, m, now);
                    }
                }
                updateMeta(session.getSessionId(),
                        StringUtils.hasText(session.getSource()) ? session.getSource() : null,
                        session.getCompactAnchorSeq(),
                        session.getLastRunId(),
                        (int) (nextSeq - 1L),
                        session.getUpdatedAt() != null ? session.getUpdatedAt() : Instant.now());
                commitTx();
                return findUnlocked(session.getSessionId())
                        .orElseThrow(() -> new IllegalStateException("save lost session"));
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("save failed", e);
            }
        }
    }

    @Override
    public Optional<Session> find(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        synchronized (lock) {
            try {
                return findUnlocked(sessionId);
            } catch (SQLException e) {
                throw new IllegalStateException("find failed", e);
            }
        }
    }

    @Override
    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        synchronized (lock) {
            try {
                beginImmediate();
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM pi_session_message WHERE session_id = ?")) {
                    ps.setString(1, sessionId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM pi_session WHERE session_id = ?")) {
                    ps.setString(1, sessionId);
                    ps.executeUpdate();
                }
                commitTx();
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("delete failed", e);
            }
        }
    }

    @Override
    public Session getOrCreate(Session.Meta meta) {
        Objects.requireNonNull(meta, "meta");
        String sessionId = StringUtils.hasText(meta.getSessionId())
                ? meta.getSessionId().trim()
                : UUID.randomUUID().toString();
        synchronized (lock) {
            try {
                beginImmediate();
                Optional<Session> existing = findUnlocked(sessionId);
                if (existing.isPresent()) {
                    commitTx();
                    return existing.get();
                }
                Instant now = Instant.now();
                String source = StringUtils.hasText(meta.getSource()) ? meta.getSource().trim() : "api";
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO pi_session ("
                                + "session_id, title, source, status, parent_session_id, "
                                + "compact_anchor_seq, message_count, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'active', ?, 0, 0, ?, ?)")) {
                    ps.setString(1, sessionId);
                    ps.setString(2, meta.getTitle());
                    ps.setString(3, source);
                    ps.setString(4, meta.getParentSessionId());
                    ps.setString(5, now.toString());
                    ps.setString(6, now.toString());
                    ps.executeUpdate();
                }
                commitTx();
                return findUnlocked(sessionId)
                        .orElseThrow(() -> new IllegalStateException("getOrCreate lost row"));
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } catch (SQLException e) {
                rollbackQuietly();
                if (isUniqueViolation(e)) {
                    try {
                        return findUnlocked(sessionId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "getOrCreate unique race but row missing", e));
                    } catch (SQLException findEx) {
                        throw new IllegalStateException("getOrCreate failed after unique race", findEx);
                    }
                }
                throw new IllegalStateException("getOrCreate failed", e);
            }
        }
    }

    @Override
    public List<Message> load(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Collections.emptyList();
        }
        synchronized (lock) {
            try {
                long anchor = compactAnchor(sessionId);
                if (anchor < 0L) {
                    return Collections.emptyList();
                }
                return loadProjected(sessionId, anchor);
            } catch (SQLException e) {
                throw new IllegalStateException("load failed", e);
            }
        }
    }

    @Override
    public void append(String sessionId, String runId, List<Message> messages) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        synchronized (lock) {
            try {
                beginImmediate();
                if (!sessionExists(sessionId)) {
                    throw new IllegalArgumentException("session not found: " + sessionId);
                }
                if (StringUtils.hasText(runId) && runIdExists(sessionId, runId)) {
                    commitTx();
                    return;
                }
                if (messages == null || messages.isEmpty()) {
                    commitTx();
                    return;
                }
                long nextSeq = nextSeq(sessionId);
                Instant now = Instant.now();
                boolean any = false;
                for (Message m : messages) {
                    if (m == null || isSystem(m)) {
                        continue;
                    }
                    insertMessage(sessionId, nextSeq++, runId, m, now);
                    any = true;
                }
                if (any) {
                    bumpSessionAfterAppend(sessionId, runId, now);
                }
                commitTx();
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("appendMessages failed", e);
            }
        }
    }

    @Override
    public void setCompactAnchor(String sessionId, long seq, Message summaryMessage) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId required");
        }
        synchronized (lock) {
            try {
                beginImmediate();
                if (!sessionExists(sessionId)) {
                    throw new IllegalArgumentException("session not found: " + sessionId);
                }
                long next = nextSeq(sessionId);
                if (seq < 0L || seq >= next) {
                    throw new IllegalArgumentException(
                            "compact anchor seq out of range: " + seq + " (nextSeq=" + next + ")");
                }
                long current = compactAnchor(sessionId);
                if (current == seq) {
                    commitTx();
                    return;
                }
                Instant now = Instant.now();
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE pi_session SET compact_anchor_seq = ?, updated_at = ? "
                                + "WHERE session_id = ?")) {
                    ps.setLong(1, seq);
                    ps.setString(2, now.toString());
                    ps.setString(3, sessionId);
                    ps.executeUpdate();
                }
                if (summaryMessage != null && !isSystem(summaryMessage)) {
                    String compactRunId = "compact-" + seq + "-" + next;
                    insertMessage(sessionId, next, compactRunId, summaryMessage, now);
                    bumpMessageCountOnly(sessionId, now);
                }
                commitTx();
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("setCompactAnchor failed", e);
            }
        }
    }

    private static final int LIST_LIMIT_MIN = 1;
    private static final int LIST_LIMIT_MAX = 200;

    @Override
    public List<SessionSummary> listRecent(int limit) {
        int clamped = clampLimit(limit);
        synchronized (lock) {
            try {
                return querySummaries(
                        "SELECT session_id, parent_session_id, title, source, status, "
                                + "message_count, updated_at, last_run_id "
                                + "FROM pi_session ORDER BY updated_at DESC LIMIT ?",
                        ps -> ps.setInt(1, clamped));
            } catch (SQLException e) {
                throw new IllegalStateException("listRecent failed", e);
            }
        }
    }

    @Override
    public List<SessionSummary> listChildren(String parentSessionId) {
        synchronized (lock) {
            try {
                if (!StringUtils.hasText(parentSessionId)) {
                    return querySummaries(
                            "SELECT session_id, parent_session_id, title, source, status, "
                                    + "message_count, updated_at, last_run_id "
                                    + "FROM pi_session WHERE parent_session_id IS NULL "
                                    + "ORDER BY updated_at DESC",
                            ps -> {
                            });
                }
                return querySummaries(
                        "SELECT session_id, parent_session_id, title, source, status, "
                                + "message_count, updated_at, last_run_id "
                                + "FROM pi_session WHERE parent_session_id = ? "
                                + "ORDER BY updated_at DESC",
                        ps -> ps.setString(1, parentSessionId.trim()));
            } catch (SQLException e) {
                throw new IllegalStateException("listChildren failed", e);
            }
        }
    }

    @Override
    public Optional<SessionSummary> findSummary(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        synchronized (lock) {
            try {
                List<SessionSummary> rows = querySummaries(
                        "SELECT session_id, parent_session_id, title, source, status, "
                                + "message_count, updated_at, last_run_id "
                                + "FROM pi_session WHERE session_id = ?",
                        ps -> ps.setString(1, sessionId.trim()));
                return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
            } catch (SQLException e) {
                throw new IllegalStateException("findSummary failed", e);
            }
        }
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        synchronized (lock) {
            try {
                beginImmediate();
                Instant now = Instant.now();
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE pi_session SET title = ?, updated_at = ? WHERE session_id = ?")) {
                    if (StringUtils.hasText(title)) {
                        ps.setString(1, title.trim());
                    } else {
                        ps.setNull(1, java.sql.Types.VARCHAR);
                    }
                    ps.setString(2, now.toString());
                    ps.setString(3, sessionId.trim());
                    ps.executeUpdate();
                }
                commitTx();
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IllegalStateException("updateTitle failed", e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.warn("close SqliteSessionStore failed: {}", e.toString());
            }
        }
    }

    // —— helpers ——

    private void beginImmediate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("BEGIN IMMEDIATE");
        }
    }

    private void commitTx() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("COMMIT");
        }
    }

    private void rollbackQuietly() {
        try (Statement st = connection.createStatement()) {
            st.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // best-effort（可能本无未开事务）
        }
    }

    private Optional<Session> findUnlocked(String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT session_id, title, source, parent_session_id, compact_anchor_seq, "
                        + "last_run_id, message_count, created_at, updated_at "
                        + "FROM pi_session WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long anchor = rs.getLong("compact_anchor_seq");
                String rowSession = rs.getString("session_id");
                String title = rs.getString("title");
                String source = rs.getString("source");
                String parentSessionId = rs.getString("parent_session_id");
                String lastRunId = rs.getString("last_run_id");
                int messageCount = rs.getInt("message_count");
                Instant createdAt = parseInstant(rs.getString("created_at"));
                Instant updatedAt = parseInstant(rs.getString("updated_at"));
                List<Message> projected = loadProjected(sessionId, anchor);
                return Optional.of(Session.builder()
                        .sessionId(rowSession)
                        .title(title)
                        .source(source)
                        .parentSessionId(parentSessionId)
                        .compactAnchorSeq(anchor)
                        .lastRunId(lastRunId)
                        .messageCount(messageCount)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .messages(projected)
                        .build());
            }
        }
    }

    private List<Message> loadProjected(String sessionId, long anchor) throws SQLException {
        List<Message> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT role, content, payload_json FROM pi_session_message "
                        + "WHERE session_id = ? AND seq > ? "
                        + "ORDER BY seq ASC")) {
            ps.setString(1, sessionId);
            ps.setLong(2, anchor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    if ("system".equalsIgnoreCase(role)) {
                        continue;
                    }
                    out.add(rowToMessage(role, rs.getString("content"), rs.getString("payload_json")));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    private boolean sessionExists(String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM pi_session WHERE session_id = ? LIMIT 1")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean runIdExists(String sessionId, String runId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM pi_session_message "
                        + "WHERE session_id = ? AND run_id = ? LIMIT 1")) {
            ps.setString(1, sessionId);
            ps.setString(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private long nextSeq(String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(seq), 0) + 1 AS next_seq FROM pi_session_message "
                        + "WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("next_seq");
            }
        }
    }

    /** 无会话行时返回 -1；否则返回锚点。 */
    private long compactAnchor(String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT compact_anchor_seq FROM pi_session WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return -1L;
                }
                return rs.getLong(1);
            }
        }
    }

    private void ensureSessionRow(String sessionId, String source,
                                  Instant createdAt, Instant updatedAt) throws SQLException {
        if (sessionExists(sessionId)) {
            return;
        }
        Instant c = createdAt != null ? createdAt : Instant.now();
        Instant u = updatedAt != null ? updatedAt : c;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pi_session ("
                        + "session_id, source, status, "
                        + "compact_anchor_seq, message_count, created_at, updated_at) "
                        + "VALUES (?, ?, 'active', 0, 0, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setString(2, source != null ? source : "api");
            ps.setString(3, c.toString());
            ps.setString(4, u.toString());
            ps.executeUpdate();
        }
    }

    private void deleteMessages(String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM pi_session_message WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    private void insertMessage(String sessionId, long seq, String runId,
                               Message message, Instant createdAt) throws SQLException {
        if (message == null || !StringUtils.hasText(message.getRole())) {
            throw new IllegalArgumentException("message.role required");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pi_session_message ("
                        + "session_id, seq, run_id, role, content, payload_json, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setLong(2, seq);
            ps.setString(3, runId);
            ps.setString(4, message.getRole());
            ps.setString(5, message.getContent());
            ps.setString(6, toPayloadJson(message));
            ps.setString(7, createdAt.toString());
            ps.executeUpdate();
        }
    }

    private void bumpSessionAfterAppend(String sessionId, String runId, Instant now)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pi_session SET last_run_id = ?, message_count = ("
                        + "SELECT COUNT(*) FROM pi_session_message "
                        + "WHERE session_id = ?), updated_at = ? "
                        + "WHERE session_id = ?")) {
            ps.setString(1, runId);
            ps.setString(2, sessionId);
            ps.setString(3, now.toString());
            ps.setString(4, sessionId);
            ps.executeUpdate();
        }
    }

    /** compact 摘要行：只刷新计数与时间，保留既有 last_run_id（对齐 InMemory）。 */
    private void bumpMessageCountOnly(String sessionId, Instant now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pi_session SET message_count = ("
                        + "SELECT COUNT(*) FROM pi_session_message "
                        + "WHERE session_id = ?), updated_at = ? "
                        + "WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ps.setString(2, now.toString());
            ps.setString(3, sessionId);
            ps.executeUpdate();
        }
    }

    private void updateMeta(String sessionId, String source,
                            long compactAnchorSeq, String lastRunId, int messageCount, Instant updatedAt)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pi_session SET source = COALESCE(?, source), "
                        + "compact_anchor_seq = ?, last_run_id = ?, message_count = ?, updated_at = ? "
                        + "WHERE session_id = ?")) {
            ps.setString(1, source);
            ps.setLong(2, compactAnchorSeq);
            ps.setString(3, lastRunId);
            ps.setInt(4, messageCount);
            ps.setString(5, updatedAt.toString());
            ps.setString(6, sessionId);
            ps.executeUpdate();
        }
    }

    static String toPayloadJson(Message message) {
        if (message == null) {
            return null;
        }
        boolean hasParts = message.hasParts();
        boolean hasToolCallId = StringUtils.hasText(message.getToolCallId());
        boolean hasToolCalls = message.getToolCalls() != null && !message.getToolCalls().isEmpty();
        if (!hasParts && !hasToolCallId && !hasToolCalls) {
            return null;
        }
        try {
            ObjectNode node = MAPPER.createObjectNode();
            if (hasParts) {
                node.set("parts", MAPPER.valueToTree(message.getParts()));
            }
            if (hasToolCallId) {
                node.put("toolCallId", message.getToolCallId());
            }
            if (hasToolCalls) {
                ArrayNode arr = node.putArray("toolCalls");
                for (ToolCallEntry tc : message.getToolCalls()) {
                    if (tc == null) {
                        continue;
                    }
                    ObjectNode t = arr.addObject();
                    if (tc.getId() != null) {
                        t.put("id", tc.getId());
                    }
                    if (tc.getToolName() != null) {
                        t.put("toolName", tc.getToolName());
                    }
                    if (tc.getArguments() != null) {
                        t.set("arguments", tc.getArguments());
                    }
                }
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("payload_json serialize failed", e);
        }
    }

    static Message rowToMessage(String role, String content, String payloadJson) {
        Message.MessageBuilder builder = Message.builder()
                .role(role)
                .content(content);
        if (!StringUtils.hasText(payloadJson)) {
            return builder.build();
        }
        try {
            JsonNode root = MAPPER.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("payload_json must be object");
            }
            if (root.has("toolCallId") && !root.get("toolCallId").isNull()) {
                builder.toolCallId(root.get("toolCallId").asText());
            }
            if (root.has("parts") && root.get("parts").isArray()) {
                List<ContentPart> parts = new ArrayList<>();
                for (JsonNode p : root.get("parts")) {
                    if (p == null || p.isNull()) {
                        continue;
                    }
                    parts.add(ContentPart.builder()
                            .type(textOrNull(p, "type"))
                            .text(textOrNull(p, "text"))
                            .url(textOrNull(p, "url"))
                            .detail(textOrNull(p, "detail"))
                            .build());
                }
                builder.parts(parts);
            }
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                List<ToolCallEntry> calls = new ArrayList<>();
                for (JsonNode t : root.get("toolCalls")) {
                    if (t == null || t.isNull()) {
                        continue;
                    }
                    calls.add(new ToolCallEntry(
                            textOrNull(t, "id"),
                            textOrNull(t, "toolName"),
                            t.get("arguments")));
                }
                builder.toolCalls(calls);
            }
            return builder.build();
        } catch (IOException e) {
            throw new IllegalStateException("payload_json deserialize failed", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    private static Instant parseInstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("invalid timestamp: " + raw, e);
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        if (e.getErrorCode() == 19) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase().contains("UNIQUE");
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

    @FunctionalInterface
    private interface PreparedStatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<SessionSummary> querySummaries(String sql, PreparedStatementBinder binder)
            throws SQLException {
        List<SessionSummary> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rowToSummary(rs));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static SessionSummary rowToSummary(ResultSet rs) throws SQLException {
        return SessionSummary.builder()
                .sessionId(rs.getString("session_id"))
                .parentSessionId(rs.getString("parent_session_id"))
                .title(rs.getString("title"))
                .source(rs.getString("source"))
                .status(rs.getString("status"))
                .messageCount(rs.getInt("message_count"))
                .updatedAt(parseInstant(rs.getString("updated_at")))
                .lastRunId(rs.getString("last_run_id"))
                .build();
    }
}

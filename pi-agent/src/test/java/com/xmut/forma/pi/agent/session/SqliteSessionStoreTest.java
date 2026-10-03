package com.xmut.forma.pi.agent.session;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.message.ContentPart;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteSessionStoreTest {

    @TempDir
    Path tempDir;

    private Path dbFile() {
        return tempDir.resolve("state.db");
    }

    @Test
    void creates_parent_dir_and_db_file_on_open() throws Exception {
        Path nested = tempDir.resolve(".lippi-pi").resolve("state.db");
        try (SqliteSessionStore store = new SqliteSessionStore(nested)) {
            assertThat(Files.isDirectory(nested.getParent())).isTrue();
            assertThat(Files.exists(nested)).isTrue();
            assertThat(store.getDbPath()).isEqualTo(nested.toAbsolutePath().normalize());
        }
    }

    @Test
    void getOrCreate_persists_parent_and_title() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder()
                    .sessionId("s-child")
                    .source("cli")
                    .parentSessionId("s-parent")
                    .title("demo")
                    .build());
            Optional<Session> found = store.find("s-child");
            assertThat(found).isPresent();
            assertThat(found.get().getParentSessionId()).isEqualTo("s-parent");
            assertThat(found.get().getTitle()).isEqualTo("demo");
        }
    }

    @Test
    void getOrCreate_blank_session_allocates_id() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session s = store.getOrCreate(Session.Meta.builder().source("cli").build());
            assertThat(s.getSessionId()).isNotBlank();
            assertThat(s.getSource()).isEqualTo("cli");
            assertThat(store.load(s.getSessionId())).isEmpty();
        }
    }

    @Test
    void find_and_load_by_session_id_only() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session s = store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "r1", Collections.singletonList(
                    Message.builder().role("user").content("hi").build()));
            assertThat(s.getSessionId()).isEqualTo("s1");
            assertThat(store.find("s1")).isPresent();
            assertThat(store.load("s1")).hasSize(1);
            assertThat(store.find("other")).isEmpty();
        }
    }

    @Test
    void getOrCreate_creates_stable_session_and_empty_load() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session a = store.getOrCreate(Session.Meta.builder().build());
            assertThat(a.getSessionId()).isNotBlank();
            assertThat(store.load(a.getSessionId())).isEmpty();

            Session b = store.getOrCreate(Session.Meta.builder()
                    .sessionId(a.getSessionId())
                    .build());
            assertThat(b.getSessionId()).isEqualTo(a.getSessionId());
        }
    }

    @Test
    void append_assigns_monotonic_seq_and_load_projects() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session s = store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append(s.getSessionId(), "run-1", Arrays.asList(
                    Message.user("hi"),
                    Message.assistant("hello", Collections.emptyList()),
                    Message.system("ignored")));

            List<Message> loaded = store.load("s1");
            assertThat(loaded).hasSize(2);
            assertThat(loaded.get(0).getContent()).isEqualTo("hi");
            assertThat(loaded.get(1).getContent()).isEqualTo("hello");
            assertThat(loaded).noneMatch(m -> "system".equalsIgnoreCase(m.getRole()));
        }
    }

    @Test
    void append_same_runId_is_idempotent() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "run-1",
                    Collections.singletonList(Message.user("a")));
            store.append("s1", "run-1",
                    Collections.singletonList(Message.user("a-dup")));
            assertThat(store.load("s1")).hasSize(1);
            assertThat(store.load("s1").get(0).getContent()).isEqualTo("a");
        }
    }

    @Test
    void append_all_system_does_not_block_same_runId_retry() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "run-1",
                    Collections.singletonList(Message.system("sys")));
            store.append("s1", "run-1",
                    Collections.singletonList(Message.user("real")));
            assertThat(store.load("s1")).extracting(Message::getContent)
                    .containsExactly("real");
        }
    }

    @Test
    void append_empty_batch_does_not_register_runId() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "run-1", Collections.emptyList());
            store.append("s1", "run-1",
                    Collections.singletonList(Message.user("after-empty")));
            assertThat(store.load("s1")).extracting(Message::getContent)
                    .containsExactly("after-empty");
        }
    }

    @Test
    void setCompactAnchor_hides_messages_at_or_before_anchor() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "r1", Arrays.asList(
                    Message.user("old"),
                    Message.assistant("old-a", Collections.emptyList())));

            store.setCompactAnchor("s1", 2L, Message.assistant("summary", Collections.emptyList()));
            store.append("s1", "r2",
                    Collections.singletonList(Message.user("new")));

            List<Message> loaded = store.load("s1");
            assertThat(loaded).extracting(Message::getContent)
                    .containsExactly("summary", "new");
            assertThat(store.find("s1").get().getCompactAnchorSeq()).isEqualTo(2L);
        }
    }

    @Test
    void setCompactAnchor_out_of_range_rejected() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "r1",
                    Collections.singletonList(Message.user("only")));
            assertThatThrownBy(() -> store.setCompactAnchor("s1", 99L,
                    Message.assistant("summary", Collections.emptyList())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("out of range");
        }
    }

    @Test
    void setCompactAnchor_same_seq_idempotent_no_duplicate_summary() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "r1", Arrays.asList(
                    Message.user("old"),
                    Message.assistant("old-a", Collections.emptyList())));
            store.setCompactAnchor("s1", 2L, Message.assistant("summary", Collections.emptyList()));
            store.setCompactAnchor("s1", 2L, Message.assistant("summary-2", Collections.emptyList()));
            assertThat(store.load("s1")).extracting(Message::getContent)
                    .containsExactly("summary");
        }
    }

    @Test
    void setCompactAnchor_with_summary_preserves_last_run_id() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            store.append("s1", "turn-run", Arrays.asList(
                    Message.user("old"),
                    Message.assistant("old-a", Collections.emptyList())));
            assertThat(store.find("s1").get().getLastRunId()).isEqualTo("turn-run");

            store.setCompactAnchor("s1", 2L, Message.assistant("summary", Collections.emptyList()));
            assertThat(store.find("s1").get().getLastRunId()).isEqualTo("turn-run");
            assertThat(store.load("s1")).extracting(Message::getContent)
                    .containsExactly("summary");
        }
    }

    @Test
    void append_blank_role_rejected() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            Message bad = Message.builder().role("").content("x").build();
            assertThatThrownBy(() -> store.append("s1", "r1",
                    Collections.singletonList(bad)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("message.role required");
        }
    }

    @Test
    void payload_json_non_object_fail_closed() {
        assertThatThrownBy(() -> SqliteSessionStore.rowToMessage("user", "c", "[1,2]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payload_json must be object");
    }

    @Test
    void allows_colon_in_session_id() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session s = store.getOrCreate(Session.Meta.builder()
                    .sessionId("a:b")
                    .build());
            store.append("a:b", "r1",
                    Collections.singletonList(Message.user("ok")));
            assertThat(store.load(s.getSessionId())).extracting(Message::getContent)
                    .containsExactly("ok");
        }
    }

    @Test
    void payload_json_round_trips_parts_and_tool_fields() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
            Message multimodal = Message.user(Arrays.asList(
                    ContentPart.text("see"),
                    ContentPart.imageUrl("https://example.com/a.png")));
            Message withTools = Message.assistant("call", Collections.singletonList(
                    new ToolCallEntry("c1", "sample.echo",
                            JsonNodeFactory.instance.objectNode().put("x", 1))));
            Message tool = Message.tool("c1", "result");
            store.append("s1", "r1", Arrays.asList(multimodal, withTools, tool));

            List<Message> loaded = store.load("s1");
            assertThat(loaded).hasSize(3);
            assertThat(loaded.get(0).hasParts()).isTrue();
            assertThat(loaded.get(0).getParts().get(1).getUrl()).isEqualTo("https://example.com/a.png");
            assertThat(loaded.get(1).getToolCalls()).hasSize(1);
            assertThat(loaded.get(1).getToolCalls().get(0).getToolName()).isEqualTo("sample.echo");
            assertThat(loaded.get(2).getToolCallId()).isEqualTo("c1");
        }
    }

    @Test
    void cross_instance_reopen_same_path_sees_messages() {
        Path db = dbFile();
        try (SqliteSessionStore storeA = new SqliteSessionStore(db)) {
            storeA.getOrCreate(Session.Meta.builder().sessionId("s-cross").build());
            storeA.append("s-cross", "r1", Arrays.asList(
                    Message.user("from-a"),
                    Message.assistant("reply-a", Collections.emptyList())));
        }
        try (SqliteSessionStore storeB = new SqliteSessionStore(db)) {
            assertThat(storeB.load("s-cross")).extracting(Message::getContent)
                    .containsExactly("from-a", "reply-a");
            assertThat(storeB.find("s-cross")).isPresent();
        }
    }

    @Test
    void save_find_delete_roundTrip() {
        try (SqliteSessionStore store = new SqliteSessionStore(dbFile())) {
            Session session = Session.builder()
                    .sessionId("s1")
                    .messages(Collections.singletonList(Message.user("saved")))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            store.save(session);
            assertThat(store.find("s1")).isPresent();
            assertThat(store.load("s1")).extracting(Message::getContent)
                    .containsExactly("saved");
            store.delete("s1");
            assertThat(store.find("s1")).isEmpty();
            assertThat(store.load("s1")).isEmpty();
        }
    }

    @Test
    void resolveSqlitePath_blank_uses_user_dir_default() {
        Path resolved = SqliteSessionStore.resolveSqlitePath("");
        assertThat(resolved.getFileName().toString()).isEqualTo("state.db");
        assertThat(resolved.getParent().getFileName().toString()).isEqualTo(".lippi-pi");
        assertThat(resolved.isAbsolute()).isTrue();
    }

    @Test
    void resolveSqlitePath_relative_resolves_against_cwd() {
        Path resolved = SqliteSessionStore.resolveSqlitePath("tmp/pi-test.db");
        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.getFileName().toString()).isEqualTo("pi-test.db");
    }

    @Test
    void new_db_sets_schema_user_version_1() throws Exception {
        Path db = dbFile();
        try (SqliteSessionStore ignored = new SqliteSessionStore(db)) {
            assertThat(SqliteSessionStore.SCHEMA_USER_VERSION).isEqualTo(1);
        }
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void rejects_legacy_schema_with_tenant_id() throws Exception {
        Path db = dbFile();
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE pi_session ("
                    + "id INTEGER PRIMARY KEY, tenant_id TEXT NOT NULL, session_id TEXT NOT NULL)");
            st.execute("PRAGMA user_version = 0");
        }
        String path = db.toAbsolutePath().normalize().toString();
        assertThatThrownBy(() -> new SqliteSessionStore(db))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path)
                .hasMessageContaining("请删除旧会话库后重试");
    }

    @Test
    void rejects_legacy_user_version_when_tables_exist() throws Exception {
        Path db = dbFile();
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE pi_session (id INTEGER PRIMARY KEY, session_id TEXT NOT NULL)");
            st.execute("PRAGMA user_version = 0");
        }
        String path = db.toAbsolutePath().normalize().toString();
        assertThatThrownBy(() -> new SqliteSessionStore(db))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(path)
                .hasMessageContaining("请删除旧会话库后重试");
    }
}

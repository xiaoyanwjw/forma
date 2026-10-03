package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreListTest {

    @TempDir
    Path tempDir;

    @Test
    void listRecent_orders_by_updated_desc_inMemory() {
        listRecent_orders_by_updated_desc(new InMemorySessionStore());
    }

    @Test
    void listRecent_orders_by_updated_desc_sqlite() throws Exception {
        try (SqliteSessionStore store = new SqliteSessionStore(tempDir.resolve("state.db"))) {
            listRecent_orders_by_updated_desc(store);
        }
    }

    @Test
    void listChildren_roots_and_nested_inMemory() {
        listChildren_roots_and_nested(new InMemorySessionStore());
    }

    @Test
    void listChildren_roots_and_nested_sqlite() throws Exception {
        try (SqliteSessionStore store = new SqliteSessionStore(tempDir.resolve("state.db"))) {
            listChildren_roots_and_nested(store);
        }
    }

    @Test
    void updateTitle_bumpsUpdatedAt_andReordersListRecent_inMemory() {
        updateTitle_bumpsUpdatedAt_andReordersListRecent(new InMemorySessionStore());
    }

    @Test
    void updateTitle_bumpsUpdatedAt_andReordersListRecent_sqlite() throws Exception {
        try (SqliteSessionStore store = new SqliteSessionStore(tempDir.resolve("state.db"))) {
            updateTitle_bumpsUpdatedAt_andReordersListRecent(store);
        }
    }

    @Test
    void updateTitle_roundtrip_inMemory() {
        updateTitle_roundtrip(new InMemorySessionStore());
    }

    @Test
    void updateTitle_roundtrip_sqlite() throws Exception {
        try (SqliteSessionStore store = new SqliteSessionStore(tempDir.resolve("state.db"))) {
            updateTitle_roundtrip(store);
        }
    }

    private void listRecent_orders_by_updated_desc(SessionStore store) {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
        store.save(Session.builder().sessionId("older").createdAt(t0).updatedAt(t0).build());
        store.save(Session.builder().sessionId("newer").createdAt(t0).updatedAt(t1).build());
        store.append("older", "r1", Collections.singletonList(Message.user("bump")));
        List<SessionSummary> recent = store.listRecent(50);
        assertThat(recent).hasSizeGreaterThanOrEqualTo(2);
        assertThat(recent.get(0).getSessionId()).isEqualTo("older");
        assertThat(recent.get(1).getSessionId()).isEqualTo("newer");
    }

    private void listChildren_roots_and_nested(SessionStore store) {
        store.getOrCreate(Session.Meta.builder().sessionId("root-a").build());
        store.getOrCreate(Session.Meta.builder()
                .sessionId("child-b")
                .parentSessionId("root-a")
                .build());
        List<SessionSummary> roots = store.listChildren(null);
        assertThat(roots).extracting(SessionSummary::getSessionId).contains("root-a");
        assertThat(roots).extracting(SessionSummary::getSessionId).doesNotContain("child-b");
        List<SessionSummary> children = store.listChildren("root-a");
        assertThat(children).extracting(SessionSummary::getSessionId).containsExactly("child-b");
    }

    private void updateTitle_bumpsUpdatedAt_andReordersListRecent(SessionStore store) {
        Instant older = Instant.parse("2026-01-01T00:00:00Z");
        Instant newer = Instant.parse("2026-01-01T00:00:01Z");
        store.save(Session.builder().sessionId("first").title("a").createdAt(older).updatedAt(newer).build());
        store.save(Session.builder().sessionId("second").title("b").createdAt(older).updatedAt(older).build());
        Instant beforeRename = store.findSummary("second").get().getUpdatedAt();
        store.updateTitle("second", "renamed-second");
        Instant afterRename = store.findSummary("second").get().getUpdatedAt();
        assertThat(afterRename).isAfter(beforeRename);
        List<SessionSummary> recent = store.listRecent(50);
        assertThat(recent.get(0).getSessionId()).isEqualTo("second");
        assertThat(recent.get(0).getTitle()).isEqualTo("renamed-second");
    }

    private void updateTitle_roundtrip(SessionStore store) {
        store.getOrCreate(Session.Meta.builder()
                .sessionId("s1")
                .title("initial")
                .build());
        store.updateTitle("s1", "renamed");
        Optional<SessionSummary> summary = store.findSummary("s1");
        assertThat(summary).isPresent();
        assertThat(summary.get().getTitle()).isEqualTo("renamed");
        store.updateTitle("s1", "  ");
        assertThat(store.findSummary("s1").get().getTitle()).isNull();
    }
}

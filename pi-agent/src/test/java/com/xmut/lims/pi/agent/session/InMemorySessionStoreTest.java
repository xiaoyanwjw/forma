package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySessionStoreTest {

    @Test
    void getOrCreate_persists_parent_and_title() {
        InMemorySessionStore store = new InMemorySessionStore();
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

    @Test
    void save_find_delete_roundTrip() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = Session.builder()
                .sessionId("s1")
                .messages(Collections.emptyList())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        store.save(session);
        assertThat(store.find("s1")).isPresent();
        assertThat(store.find("s1").get().getSessionId()).isEqualTo("s1");
        store.delete("s1");
        assertThat(store.find("s1")).isEmpty();
    }

    @Test
    void getOrCreate_blank_session_allocates_id() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session s = store.getOrCreate(Session.Meta.builder().source("cli").build());
        assertThat(s.getSessionId()).isNotBlank();
        assertThat(s.getSource()).isEqualTo("cli");
        assertThat(store.load(s.getSessionId())).isEmpty();
    }

    @Test
    void find_and_load_by_session_id_only() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session s = store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        store.append("s1", "r1", Collections.singletonList(
                Message.builder().role("user").content("hi").build()));
        assertThat(s.getSessionId()).isEqualTo("s1");
        assertThat(store.find("s1")).isPresent();
        assertThat(store.load("s1")).hasSize(1);
        assertThat(store.find("other")).isEmpty();
    }

    @Test
    void getOrCreate_creates_stable_session_and_empty_load() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session a = store.getOrCreate(Session.Meta.builder().build());
        assertThat(a.getSessionId()).isNotBlank();
        assertThat(store.load(a.getSessionId())).isEmpty();

        Session b = store.getOrCreate(Session.Meta.builder()
                .sessionId(a.getSessionId())
                .build());
        assertThat(b.getSessionId()).isEqualTo(a.getSessionId());
    }

    @Test
    void append_assigns_monotonic_seq_and_load_projects() {
        InMemorySessionStore store = new InMemorySessionStore();
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

    @Test
    void append_same_runId_is_idempotent() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        store.append("s1", "run-1",
                Collections.singletonList(Message.user("a")));
        store.append("s1", "run-1",
                Collections.singletonList(Message.user("a-dup")));
        assertThat(store.load("s1")).hasSize(1);
        assertThat(store.load("s1").get(0).getContent()).isEqualTo("a");
    }

    @Test
    void append_all_system_does_not_block_same_runId_retry() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        store.append("s1", "run-1",
                Collections.singletonList(Message.system("sys")));
        store.append("s1", "run-1",
                Collections.singletonList(Message.user("real")));
        assertThat(store.load("s1")).extracting(Message::getContent)
                .containsExactly("real");
    }

    @Test
    void setCompactAnchor_hides_messages_at_or_before_anchor() {
        InMemorySessionStore store = new InMemorySessionStore();
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

    @Test
    void setCompactAnchor_out_of_range_rejected() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        store.append("s1", "r1",
                Collections.singletonList(Message.user("only")));
        assertThatThrownBy(() -> store.setCompactAnchor("s1", 99L,
                Message.assistant("summary", Collections.emptyList())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void setCompactAnchor_same_seq_idempotent_no_duplicate_summary() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        store.append("s1", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));
        store.setCompactAnchor("s1", 2L, Message.assistant("summary", Collections.emptyList()));
        store.setCompactAnchor("s1", 2L, Message.assistant("summary-2", Collections.emptyList()));
        assertThat(store.load("s1")).extracting(Message::getContent)
                .containsExactly("summary");
    }

    @Test
    void key_rejects_colon_in_session_id() {
        InMemorySessionStore store = new InMemorySessionStore();
        assertThatThrownBy(() -> store.getOrCreate(Session.Meta.builder()
                .sessionId("a:b")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ':'");
    }
}

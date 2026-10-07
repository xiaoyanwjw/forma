package com.xmut.forma;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiSessionEntryMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiSessionEntryPO;
import com.xmut.forma.infrastructure.session.MysqlSessionStore;
import com.xmut.forma.pi.ai.message.ContentPart;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.agent.session.Session;
import com.xmut.forma.pi.agent.session.SessionStore;
import com.xmut.forma.pi.agent.session.SessionSummary;
import com.xmut.forma.pi.agent.session.SqliteSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 2.7：覆盖 MysqlSessionStore I/O 矩阵与生产 Primary 装配。
 * <p>本类跑在 H2（MODE=MySQL）替身上，不是真 MySQL；真库门禁见 {@link PiSessionMysqlContainerIT}。
 */
@SpringBootTest
@ActiveProfiles("test")
class MysqlSessionStoreIntegrationTest {

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private MysqlSessionStore mysqlSessionStore;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PiSessionEntryMapper entryMapper;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM pi_session_entry");
        jdbcTemplate.update("DELETE FROM pi_session");
    }

    @Test
    void primaryBean_isMysqlSessionStore_notSqlite() {
        assertThat(sessionStore).isSameAs(mysqlSessionStore);
        assertThat(sessionStore).isInstanceOf(MysqlSessionStore.class);
        Map<String, SessionStore> stores = applicationContext.getBeansOfType(SessionStore.class);
        assertThat(stores.values()).hasSize(1);
        assertThat(stores.values()).noneMatch(b -> b instanceof SqliteSessionStore);
        assertThat(applicationContext.getEnvironment().getProperty("forma.pi.session.sqlite-path", ""))
                .isBlank();
        assertThat(Files.notExists(
                Paths.get(System.getProperty("user.dir"), ".lippi-pi", "state.db"))).isTrue();
    }

    @Test
    void getOrCreate_persists_parent_and_title() {
        sessionStore.getOrCreate(Session.Meta.builder()
                .sessionId("s-child")
                .source("cli")
                .parentSessionId("s-parent")
                .title("demo")
                .build());
        Optional<Session> found = sessionStore.find("s-child");
        assertThat(found).isPresent();
        assertThat(found.get().getParentSessionId()).isEqualTo("s-parent");
        assertThat(found.get().getTitle()).isEqualTo("demo");
        Optional<SessionSummary> summary = sessionStore.findSummary("s-child");
        assertThat(summary).isPresent();
        assertThat(summary.get().getParentSessionId()).isEqualTo("s-parent");
        assertThat(summary.get().getTitle()).isEqualTo("demo");
    }

    @Test
    void listChildren_roots_and_nested() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("root-a").build());
        sessionStore.getOrCreate(Session.Meta.builder()
                .sessionId("child-b")
                .parentSessionId("root-a")
                .build());
        List<SessionSummary> roots = sessionStore.listChildren(null);
        assertThat(roots).extracting(SessionSummary::getSessionId).contains("root-a");
        assertThat(roots).extracting(SessionSummary::getSessionId).doesNotContain("child-b");
        List<SessionSummary> children = sessionStore.listChildren("root-a");
        assertThat(children).extracting(SessionSummary::getSessionId).containsExactly("child-b");
    }

    @Test
    void updateTitle_roundtrip_and_findSummary() {
        sessionStore.getOrCreate(Session.Meta.builder()
                .sessionId("s1")
                .title("initial")
                .build());
        sessionStore.updateTitle("s1", "renamed");
        Optional<SessionSummary> summary = sessionStore.findSummary("s1");
        assertThat(summary).isPresent();
        assertThat(summary.get().getTitle()).isEqualTo("renamed");
        sessionStore.updateTitle("s1", "  ");
        assertThat(sessionStore.findSummary("s1").get().getTitle()).isNull();
    }

    @Test
    void getOrCreate_rejects_colon_in_session_id() {
        assertThatThrownBy(() -> sessionStore.getOrCreate(Session.Meta.builder()
                .sessionId("a:b")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ':'");
    }

    @Test
    void getOrCreate_blankMeta_allocatesUuid_andEmptyTranscript() {
        Session created = sessionStore.getOrCreate(Session.Meta.builder().build());
        assertThat(created.getSessionId()).isNotBlank();
        assertThat(created.getSource()).isEqualTo("api");
        assertThat(created.getCompactAnchorSeq()).isZero();
        assertThat(sessionStore.load(created.getSessionId())).isEmpty();
    }

    @Test
    void getOrCreate_existing_returnsSame_withoutReset() {
        Session a = sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-stable").build());
        sessionStore.append("s-stable", "r1",
                Collections.singletonList(Message.user("keep")));
        Session b = sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-stable").build());
        assertThat(b.getSessionId()).isEqualTo(a.getSessionId());
        assertThat(sessionStore.load("s-stable")).extracting(Message::getContent)
                .containsExactly("keep");
    }

    @Test
    void append_oneRowPerMessage_andLoadProjects() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "run-1", Arrays.asList(
                Message.user("hi"),
                Message.assistant("hello", Collections.emptyList()),
                Message.system("ignored")));

        assertThat(sessionStore.load("s1")).extracting(Message::getContent)
                .containsExactly("hi", "hello");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_session_entry WHERE session_id = ?",
                Integer.class, "s1");
        assertThat(rows).isEqualTo(2);
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload FROM pi_session_entry WHERE session_id = ? AND seq = 1",
                String.class, "s1");
        assertThat(payload).startsWith("{").doesNotStartWith("[");
        assertThat(payload).contains("\"role\"");
    }

    @Test
    void append_sameRunId_isIdempotent() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "run-1",
                Collections.singletonList(Message.user("a")));
        sessionStore.append("s1", "run-1",
                Collections.singletonList(Message.user("a-dup")));
        assertThat(sessionStore.load("s1")).extracting(Message::getContent)
                .containsExactly("a");
    }

    @Test
    void setCompactAnchor_hidesPrior_keepsRows_andShowsSummaryPlusNew() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));
        sessionStore.setCompactAnchor("s1", 2L,
                Message.assistant("summary", Collections.emptyList()));
        sessionStore.append("s1", "r2",
                Collections.singletonList(Message.user("new")));

        assertThat(sessionStore.load("s1")).extracting(Message::getContent)
                .containsExactly("summary", "new");
        assertThat(sessionStore.find("s1").get().getCompactAnchorSeq()).isEqualTo(2L);

        List<PiSessionEntryPO> all = entryMapper.selectAllBySessionId("s1");
        assertThat(all).hasSize(4);
        assertThat(all.stream().filter(e -> e.getSeq() <= 2L)).hasSize(2);
    }

    @Test
    void setCompactAnchor_outOfRange_rejected() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1",
                Collections.singletonList(Message.user("only")));
        assertThatThrownBy(() -> sessionStore.setCompactAnchor("s1", 99L,
                Message.assistant("summary", Collections.emptyList())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
        assertThat(sessionStore.find("s1").get().getCompactAnchorSeq()).isZero();
    }

    @Test
    void setCompactAnchor_negativeSeq_rejected() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1",
                Collections.singletonList(Message.user("only")));
        assertThatThrownBy(() -> sessionStore.setCompactAnchor("s1", -1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void setCompactAnchor_sameSeq_idempotent_noDuplicateSummary() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));
        sessionStore.setCompactAnchor("s1", 2L,
                Message.assistant("summary", Collections.emptyList()));
        sessionStore.setCompactAnchor("s1", 2L,
                Message.assistant("summary-2", Collections.emptyList()));
        assertThat(sessionStore.load("s1")).extracting(Message::getContent)
                .containsExactly("summary");
    }

    @Test
    void setCompactAnchor_nullOrSystemSummary_onlyUpdatesAnchor() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));

        sessionStore.setCompactAnchor("s1", 2L, null);
        assertThat(sessionStore.load("s1")).isEmpty();
        assertThat(entryMapper.selectAllBySessionId("s1")).hasSize(2);

        // 重置锚点场景：另开会话测 system summary 不落 entry
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s2").build());
        sessionStore.append("s2", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));
        sessionStore.setCompactAnchor("s2", 1L, Message.system("sys-summary"));
        assertThat(sessionStore.load("s2")).extracting(Message::getContent)
                .containsExactly("old-a");
        assertThat(entryMapper.countBySessionId("s2")).isEqualTo(2);
    }

    @Test
    void setCompactAnchor_unknownSession_notFound() {
        assertThatThrownBy(() -> sessionStore.setCompactAnchor("missing", 0L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void listRecent_ordersByUpdatedDesc_noImplicitUserFilter() {
        Session a = sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-a").title("A").build());
        Session b = sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-b").title("B").build());
        sessionStore.append(b.getSessionId(), "rb",
                Collections.singletonList(Message.user("touch-b")));
        sessionStore.append(a.getSessionId(), "ra",
                Collections.singletonList(Message.user("touch-a")));

        List<SessionSummary> recent = sessionStore.listRecent(50);
        assertThat(recent).extracting(SessionSummary::getSessionId)
                .containsExactly("s-a", "s-b");
        // user_id 列保持 NULL；适配器不做 user 过滤
        Integer nullUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_session WHERE user_id IS NULL", Integer.class);
        assertThat(nullUsers).isEqualTo(2);
    }

    @Test
    void listRecent_clampsLimit() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        assertThat(sessionStore.listRecent(0)).hasSize(1);
        for (int i = 0; i < 5; i++) {
            sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-" + i).build());
        }
        assertThat(sessionStore.listRecent(2)).hasSize(2);
    }

    @Test
    void payload_roundTripsPartsAndToolFields() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        Message multimodal = Message.user(Arrays.asList(
                ContentPart.text("see"),
                ContentPart.imageUrl("https://example.com/a.png")));
        Message withTools = Message.assistant("call", Collections.singletonList(
                new ToolCallEntry("c1", "sample.echo",
                        JsonNodeFactory.instance.objectNode().put("x", 1))));
        Message tool = Message.tool("c1", "result");
        sessionStore.append("s1", "r1", Arrays.asList(multimodal, withTools, tool));

        List<Message> loaded = sessionStore.load("s1");
        assertThat(loaded).hasSize(3);
        assertThat(loaded.get(0).hasParts()).isTrue();
        assertThat(loaded.get(0).getParts().get(1).getUrl()).isEqualTo("https://example.com/a.png");
        assertThat(loaded.get(1).getToolCalls()).hasSize(1);
        assertThat(loaded.get(1).getToolCalls().get(0).getToolName()).isEqualTo("sample.echo");
        assertThat(loaded.get(2).getToolCallId()).isEqualTo("c1");
    }

    @Test
    void compactRunId_participatesInIdempotencySet() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s1").build());
        sessionStore.append("s1", "r1", Arrays.asList(
                Message.user("old"),
                Message.assistant("old-a", Collections.emptyList())));
        sessionStore.setCompactAnchor("s1", 2L,
                Message.assistant("summary", Collections.emptyList()));
        // compact runId = compact-2-3；再次 append 同 runId 应跳过
        sessionStore.append("s1", "compact-2-3",
                Collections.singletonList(Message.user("dup")));
        assertThat(sessionStore.load("s1")).extracting(Message::getContent)
                .containsExactly("summary");
    }
}

package com.xmut.ebus;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.ebus.infrastructure.checkpoint.MysqlCheckpointer;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.agent.DefaultAgent;
import com.xmut.lims.pi.agent.agent.DefaultToolLoopGraph;
import com.xmut.lims.pi.agent.event.DefaultPiEventBus;
import com.xmut.lims.pi.agent.event.PiEventBus;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisCheckpointer;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Story 2.8：MysqlCheckpointer 落盘矩阵与 Adam Primary 装配。
 */
@SpringBootTest
@ActiveProfiles("test")
class MysqlCheckpointerIntegrationTest {

    @Autowired
    private Checkpointer checkpointer;

    @Autowired
    private MysqlCheckpointer mysqlCheckpointer;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM pi_graph_checkpoint");
    }

    @Test
    void primaryBean_isMysqlCheckpointer_notRedisOrInMemory() {
        assertThat(checkpointer).isSameAs(mysqlCheckpointer);
        assertThat(checkpointer).isInstanceOf(MysqlCheckpointer.class);
        Map<String, Checkpointer> beans = applicationContext.getBeansOfType(Checkpointer.class);
        assertThat(beans.values()).hasSize(1);
        assertThat(beans.values()).noneMatch(b -> b instanceof RedisCheckpointer);
        assertThat(beans.values()).noneMatch(b -> b instanceof InMemoryCheckpointer);
        assertThat(applicationContext.getEnvironment()
                .getProperty("lims.pi.checkpoint.redis.enabled", "false"))
                .isEqualTo("false");
    }

    @Test
    void save_loadLatest_upsertOneRowPerRun() {
        Checkpoint first = sampleCheckpoint("run-a", "cp-1", "tools");
        Checkpoint second = sampleCheckpoint("run-a", "cp-2", "tools");
        mysqlCheckpointer.save(first);
        mysqlCheckpointer.save(second);

        Optional<Checkpoint> loaded = mysqlCheckpointer.loadLatest("run-a");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getCheckpointId()).isEqualTo("cp-2");
        assertThat(mysqlCheckpointer.load("run-a", "cp-2")).isPresent();
        assertThat(mysqlCheckpointer.load("run-a", "cp-1")).isEmpty();
        assertThat(mysqlCheckpointer.listByRun("run-a")).hasSize(1);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_graph_checkpoint WHERE run_id = ?",
                Integer.class, "run-a");
        assertThat(rows).isEqualTo(1);
        // 与 pi_session* 分表：可独立读写
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_session", Integer.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_session_entry", Integer.class)).isNotNull();
    }

    @Test
    void expired_treatedAsMissing() {
        Checkpoint cp = sampleCheckpoint("run-exp", "cp-exp", "tools");
        mysqlCheckpointer.save(cp);
        jdbcTemplate.update(
                "UPDATE pi_graph_checkpoint SET expires_at = ? WHERE run_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                "run-exp");
        assertThat(mysqlCheckpointer.loadLatest("run-exp")).isEmpty();
        assertThat(mysqlCheckpointer.listByRun("run-exp")).isEmpty();
    }

    @Test
    void deleteByRun_removesRow() {
        mysqlCheckpointer.save(sampleCheckpoint("run-del", "cp1", "tools"));
        assertThat(mysqlCheckpointer.loadLatest("run-del")).isPresent();
        mysqlCheckpointer.deleteByRun("run-del");
        assertThat(mysqlCheckpointer.loadLatest("run-del")).isEmpty();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_graph_checkpoint WHERE run_id = ?",
                Integer.class, "run-del");
        assertThat(rows).isZero();
    }

    @Test
    void hitlSuspend_persists_andAskHumanUserResume_continues() {
        AtomicInteger handlerCalls = new AtomicInteger();
        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.interrupt(call.getId(), "ask_human",
                    "{\"question\":\"确认？\",\"options\":[{\"id\":\"ok\",\"label\":\"好\"}]}");
        };
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("ask_human",
                        ToolSchema.builder().name("ask_human").build(),
                        handler)));

        AtomicInteger visits = new AtomicInteger();
        GraphNode agent = (state, ctx) -> {
            int visit = visits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "ask_human", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                mysqlCheckpointer,
                new InMemoryResumeIdempotencyStore(),
                new IterationBudget(25),
                policy,
                null);

        PiEventBus bus = new DefaultPiEventBus();
        new ToolPolicyExtension(policy, false).register(bus);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("mysql-hitl")
                .messages(Collections.singletonList(Message.user("plan")))
                .build(), bus);
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(mysqlCheckpointer.loadLatest("mysql-hitl")).isPresent();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_graph_checkpoint WHERE run_id = ?",
                Integer.class, "mysql-hitl");
        assertThat(rows).isEqualTo(1);

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("mysql-hitl")
                .toolCallId("c1")
                .humanInput("{\"selectedId\":\"confirm_execute\"}")
                .confirmId("confirm-mysql-1")
                .build(), bus);
        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(resumed.getMessages()).anyMatch(m ->
                "user".equalsIgnoreCase(m.getRole())
                        && m.getContent() != null && m.getContent().contains("confirm_execute"));
        assertThat(mysqlCheckpointer.loadLatest("mysql-hitl")).isEmpty();
    }

    @Test
    void ttlSeconds_defaultsTo7200() {
        assertThat(mysqlCheckpointer.getTtlSeconds()).isEqualTo(7200);
    }

    @Test
    void save_expiresAt_nearNowPlusTtl() {
        Instant before = Instant.now();
        mysqlCheckpointer.save(sampleCheckpoint("run-ttl", "cp-ttl", "tools"));
        Instant after = Instant.now();
        Timestamp expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM pi_graph_checkpoint WHERE run_id = ?",
                Timestamp.class, "run-ttl");
        assertThat(expiresAt).isNotNull();
        Instant expected = before.plusSeconds(mysqlCheckpointer.getTtlSeconds());
        Instant expectedHi = after.plusSeconds(mysqlCheckpointer.getTtlSeconds());
        assertThat(expiresAt.toInstant())
                .isAfterOrEqualTo(expected.minusSeconds(1))
                .isBeforeOrEqualTo(expectedHi.plusSeconds(1));
        assertThat(expiresAt.toInstant())
                .isCloseTo(before.plusSeconds(7200), within(5, ChronoUnit.SECONDS));
    }

    private static Checkpoint sampleCheckpoint(String runId, String checkpointId, String node) {
        Map<String, Object> values = new HashMap<>();
        values.put(StateKeys.MESSAGES, Collections.singletonList(Message.user("hi")));
        values.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                new ToolCallEntry("c1", "save", JsonNodeFactory.instance.objectNode())));
        return new Checkpoint(
                checkpointId, runId, 1, node,
                GraphState.create(values), Instant.now(), Collections.emptyMap());
    }
}

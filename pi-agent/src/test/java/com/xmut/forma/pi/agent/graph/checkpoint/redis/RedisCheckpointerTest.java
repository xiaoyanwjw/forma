package com.xmut.forma.pi.agent.graph.checkpoint.redis;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.forma.pi.agent.graph.GraphState;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.forma.pi.agent.graph.checkpoint.CheckpointCodec;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisCheckpointerTest {

    @Mock
    private PiRedisCommands redis;

    private final ConcurrentHashMap<String, String> kv = new ConcurrentHashMap<>();
    private RedisCheckpointer store;
    private CheckpointCodec codec;

    @BeforeEach
    void setUp() {
        kv.clear();
        codec = new CheckpointCodec();
        store = new RedisCheckpointer(redis, codec, 3600);
        lenient().doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(redis).setex(anyString(), anyInt(), anyString());
        lenient().when(redis.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            kv.remove((String) inv.getArgument(0));
            return null;
        }).when(redis).del(anyString());
    }

    @Test
    void save_loadLatest_roundTrip_typedState() {
        ToolCallEntry call = new ToolCallEntry(
                "c1", "save", JsonNodeFactory.instance.objectNode());
        Map<String, Object> values = new HashMap<>();
        values.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.assistant("x", Collections.singletonList(call))));
        values.put(StateKeys.TOOL_CALLS, Collections.singletonList(call));
        Checkpoint cp = new Checkpoint(
                "cp1", "run1", 2, "human",
                GraphState.create(values), Instant.now(), Collections.emptyMap());

        store.save(cp);

        Optional<Checkpoint> loaded = store.loadLatest("run1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getState().get(StateKeys.TOOL_CALLS)).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<ToolCallEntry> calls =
                (java.util.List<ToolCallEntry>) loaded.get().getState().get(StateKeys.TOOL_CALLS);
        assertThat(calls.get(0)).isInstanceOf(ToolCallEntry.class);
        assertThat(kv.keySet()).allMatch(k -> k.startsWith("pi:checkpoint:"));
        assertThat(kv.keySet()).noneMatch(k -> k.startsWith("agent:checkpoint:"));
        assertThat(kv.keySet()).noneMatch(k -> k.contains(":tenant"));
        assertThat(RedisCheckpointer.latestKey("run1")).isEqualTo("pi:checkpoint:run1:latest");
    }

    @Test
    void save_emptyRunId_rejected() {
        Checkpoint cp = new Checkpoint(
                "cp1", "", 0, "human",
                GraphState.empty(), Instant.now(), Collections.emptyMap());
        assertThatThrownBy(() -> store.save(cp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void deleteByRun_removesLatestHistoryAndIds() {
        Checkpoint cp = new Checkpoint(
                "cp1", "run1", 1, "human",
                GraphState.empty(), Instant.now(), Collections.emptyMap());
        store.save(cp);
        assertThat(store.loadLatest("run1")).isPresent();

        store.deleteByRun("run1");
        assertThat(store.loadLatest("run1")).isEmpty();
        assertThat(kv).isEmpty();
    }

    @Test
    void save_usesConfiguredTtl() {
        Checkpoint cp = new Checkpoint(
                "cp1", "run1", 1, "human",
                GraphState.empty(), Instant.now(), Collections.emptyMap());
        store.save(cp);
        ArgumentCaptor<Integer> ttl = ArgumentCaptor.forClass(Integer.class);
        verify(redis).setex(eq(RedisCheckpointer.latestKey("run1")), ttl.capture(), anyString());
        assertThat(ttl.getValue()).isEqualTo(3600);
    }
}

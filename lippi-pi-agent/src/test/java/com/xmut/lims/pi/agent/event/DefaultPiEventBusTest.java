package com.xmut.lims.pi.agent.event;

import com.xmut.lims.pi.agent.extension.ContextModifier;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPiEventBusTest {

    @Test
    void subscribe_exceptions_do_not_block_emit() {
        PiEventBus bus = new DefaultPiEventBus();
        AtomicInteger n = new AtomicInteger();
        bus.subscribe(e -> {
            throw new RuntimeException("boom");
        });
        bus.subscribe(e -> n.incrementAndGet());
        bus.emit(PiEvent.of(PiEventType.AGENT_START));
        assertThat(n.get()).isEqualTo(1);
    }

    @Test
    void register_is_awaited_and_reduced_for_before_tool_call() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> BeforeToolCallResult.needsHitl("reason"));
        BeforeToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(r.isNeedsHitl()).isTrue();
        assertThat(r.getReason()).isEqualTo("reason");
    }

    @Test
    void emit_invokes_subscribe_before_register() {
        PiEventBus bus = new DefaultPiEventBus();
        List<String> order = new ArrayList<String>();
        bus.subscribe(e -> order.add("observe"));
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> {
            order.add("on");
            return BeforeToolCallResult.allow();
        });
        bus.emit(PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(order).containsExactly("observe", "on");
    }

    @Test
    void before_tool_call_block_short_circuits_later_handlers() {
        PiEventBus bus = new DefaultPiEventBus();
        AtomicInteger later = new AtomicInteger();
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> BeforeToolCallResult.block("denied"));
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> {
            later.incrementAndGet();
            return BeforeToolCallResult.allow();
        });
        BeforeToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(r.isBlock()).isTrue();
        assertThat(r.getReason()).isEqualTo("denied");
        assertThat(later.get()).isZero();
    }

    @Test
    void before_tool_call_defaults_to_allow_when_no_register_handlers() {
        PiEventBus bus = new DefaultPiEventBus();
        BeforeToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(r.isAllow()).isTrue();
    }

    @Test
    void after_tool_call_last_write_wins() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.AFTER_TOOL_CALL, e -> AfterToolCallResult.of(
                ToolResult.ok("c1", "echo", "first")));
        bus.register(PiEventType.AFTER_TOOL_CALL, e -> AfterToolCallResult.of(
                ToolResult.ok("c1", "echo", "second")));
        AfterToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.AFTER_TOOL_CALL), AfterToolCallResult.class);
        assertThat(r.getToolResult().getOutput()).isEqualTo("second");
    }

    @Test
    void emit_for_subscribe_only_type_returns_null() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.AGENT_START, e -> "should-not-reduce");
        Object reduced = bus.emit(PiEvent.of(PiEventType.AGENT_START), Object.class);
        assertThat(reduced).isNull();
    }

    @Test
    void subscribe_unsubscribe_stops_delivery() throws Exception {
        PiEventBus bus = new DefaultPiEventBus();
        AtomicInteger n = new AtomicInteger();
        AutoCloseable sub = bus.subscribe(e -> n.incrementAndGet());
        sub.close();
        bus.emit(PiEvent.of(PiEventType.TURN_START));
        assertThat(n.get()).isZero();
    }

    @Test
    void clear_drops_subscribe_and_register_handlers() {
        PiEventBus bus = new DefaultPiEventBus();
        AtomicInteger observed = new AtomicInteger();
        bus.subscribe(e -> observed.incrementAndGet());
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> BeforeToolCallResult.block("stale"));
        bus.clear();
        BeforeToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(observed.get()).isZero();
        assertThat(r.isAllow()).isTrue();
    }

    @Test
    void before_agent_start_merges_append_segments_not_last_wins() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_AGENT_START, e ->
                ContextModifier.append("S1", "C1", "V1"));
        bus.register(PiEventType.BEFORE_AGENT_START, e ->
                ContextModifier.append("S2", null, "V2"));
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getAppend().getStable()).isEqualTo("S1\n\nS2");
        assertThat(r.getAppend().getContext()).isEqualTo("C1");
        assertThat(r.getAppend().getVariable()).isEqualTo("V1\n\nV2");
        assertThat(r.getOverwrite()).isNull();
    }

    @Test
    void before_agent_start_overwrite_last_non_blank_wins() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_AGENT_START, e ->
                ContextModifier.overwrite("S1", "C1", "V1"));
        bus.register(PiEventType.BEFORE_AGENT_START, e ->
                ContextModifier.overwrite("S2", null, "V2"));
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getOverwrite().getStable()).isEqualTo("S2");
        assertThat(r.getOverwrite().getContext()).isEqualTo("C1");
        assertThat(r.getOverwrite().getVariable()).isEqualTo("V2");
    }

    @Test
    void before_tool_call_register_exception_is_fail_closed() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_TOOL_CALL, e -> {
            throw new RuntimeException("policy boom");
        });
        BeforeToolCallResult r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_TOOL_CALL), BeforeToolCallResult.class);
        assertThat(r.isBlock()).isTrue();
    }
}

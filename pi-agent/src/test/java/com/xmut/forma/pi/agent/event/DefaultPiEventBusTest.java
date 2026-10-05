package com.xmut.forma.pi.agent.event;

import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().append("S1", "C1", "V1"));
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().append("S2", null, "V2"));
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
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().overwrite("S1", "C1", "V1"));
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().overwrite("S2", null, "V2"));
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getOverwrite().getStable()).isEqualTo("S2");
        assertThat(r.getOverwrite().getContext()).isEqualTo("C1");
        assertThat(r.getOverwrite().getVariable()).isEqualTo("V2");
    }

    @Test
    void before_agent_start_set_user_first_wins() {
        PiEventBus bus = new DefaultPiEventBus();
        UserModifier first = messages -> messages;
        UserModifier second = messages -> messages;
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.setUser(first));
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.setUser(second));
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getUser()).isSameAs(first);
    }

    @Test
    void before_agent_start_handler_failure_does_not_stop_later_handlers() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> {
            throw new IllegalStateException("boom");
        });
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().appendVariable("kept"));
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getAppend().getVariable()).isEqualTo("kept");
    }

    @Test
    void before_agent_start_handler_receives_event_payload() {
        PiEventBus bus = new DefaultPiEventBus();
        AtomicReference<BeforeAgentStartEvent> seen = new AtomicReference<BeforeAgentStartEvent>();
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) ->
                seen.set((BeforeAgentStartEvent) e.getPayload()));
        BeforeAgentStartEvent payload = BeforeAgentStartEvent.builder()
                .runId("r1")
                .skillId("ecommerce-skulist")
                .userText("hi")
                .build();
        bus.emit(PiEvent.of(PiEventType.BEFORE_AGENT_START, payload), ContextModifier.class);
        assertThat(seen.get()).isSameAs(payload);
        assertThat(seen.get().getSkillId()).isEqualTo("ecommerce-skulist");
    }

    @Test
    void before_agent_start_without_handlers_returns_empty_modifier() {
        PiEventBus bus = new DefaultPiEventBus();
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r).isNotNull();
        assertThat(r.getOverwrite()).isNull();
        assertThat(r.getAppend()).isNull();
        assertThat(r.getUser()).isNull();
    }

    @Test
    void register_function_for_before_agent_start_is_rejected() {
        PiEventBus bus = new DefaultPiEventBus();
        assertThatThrownBy(() -> bus.register(PiEventType.BEFORE_AGENT_START, e -> ContextModifier.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BeforeAgentStartHandler");
    }

    @Test
    void register_before_agent_start_handler_rejects_other_types() {
        PiEventBus bus = new DefaultPiEventBus();
        assertThatThrownBy(() -> bus.register(PiEventType.COMMAND, (mod, e) -> {
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BEFORE_AGENT_START");
    }

    @Test
    void before_agent_start_unregister_stops_handler() throws Exception {
        PiEventBus bus = new DefaultPiEventBus();
        AutoCloseable sub = bus.register(PiEventType.BEFORE_AGENT_START,
                (mod, e) -> mod.getSystem().appendVariable("gone"));
        sub.close();
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getAppend()).isNull();
    }

    @Test
    void clear_drops_before_agent_start_handlers() {
        PiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> mod.getSystem().appendVariable("stale"));
        bus.clear();
        ContextModifier r = bus.emit(
                PiEvent.of(PiEventType.BEFORE_AGENT_START),
                ContextModifier.class);
        assertThat(r.getAppend()).isNull();
        assertThat(r.getUser()).isNull();
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

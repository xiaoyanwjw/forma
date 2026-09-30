package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.event.AfterToolCallResult;
import com.xmut.lims.pi.agent.event.BeforeToolCallPayload;
import com.xmut.lims.pi.agent.event.BeforeToolCallResult;
import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.event.ToolSuspendPayload;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ToolNodeStreamTest {

    @Test
    void execute_emits_open_source_order_then_tool_result_message() {
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "data"));
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        Map<String, Object> updates = node.execute(
                GraphState.create(toolTurn("c1", "lookup")),
                new NodeContext("r1", "tr1", recording(events)));

        assertThat(events).extracting(PiEvent::getType).containsExactly(
                PiEventType.TOOL_EXECUTION_START,
                PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL,
                PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START,
                PiEventType.MESSAGE_END);
        ToolCallEntry start = (ToolCallEntry) events.get(0).getPayload();
        assertThat(start.getId()).isEqualTo("c1");
        assertThat(start.getToolName()).isEqualTo("lookup");
        ToolResult end = (ToolResult) events.get(3).getPayload();
        assertThat(end.getCallId()).isEqualTo("c1");
        assertThat(end.getOutput()).isEqualTo("data");
        Message toolMsg = (Message) events.get(4).getPayload();
        assertThat(toolMsg.getRole()).isEqualToIgnoringCase("tool");
        assertThat(toolMsg.getToolCallId()).isEqualTo("c1");
        assertThat(toolMsg.getContent()).isEqualTo("data");
        assertThat(updates.get(StateKeys.TOOL_CALLS)).isEqualTo(Collections.emptyList());
        assertThat(updates.get(StateKeys.INTERRUPT)).isNull();
    }

    @Test
    void two_calls_never_emit_before_tool_call_before_start() {
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "a"));
        handlers.put("save", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "b"));
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        node.execute(GraphState.create(toolTurn(
                new ToolCallEntry("c1", "lookup", JsonNodeFactory.instance.objectNode()),
                new ToolCallEntry("c2", "save", JsonNodeFactory.instance.objectNode()))),
                new NodeContext("r1", "tr1", recording(events)));

        List<PiEventType> types = new ArrayList<>();
        for (PiEvent event : events) {
            types.add(event.getType());
        }
        assertThat(types).containsExactly(
                PiEventType.TOOL_EXECUTION_START, PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL, PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START, PiEventType.MESSAGE_END,
                PiEventType.TOOL_EXECUTION_START, PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL, PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START, PiEventType.MESSAGE_END);
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) == PiEventType.BEFORE_TOOL_CALL) {
                assertThat(types.get(i - 1)).isEqualTo(PiEventType.TOOL_EXECUTION_START);
            }
        }
    }

    @Test
    void needs_hitl_on_second_call_executes_first_then_suspends() {
        AtomicInteger lookupCalls = new AtomicInteger();
        AtomicInteger saveCalls = new AtomicInteger();
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> {
            lookupCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "data");
        });
        handlers.put("save", (call, ctx) -> {
            saveCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "written");
        });
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        Emitter emitter = recording(events, event -> {
            ToolCallEntry call = BeforeToolCallPayload.callOf(event);
            if (call != null && "save".equals(call.getToolName())) {
                return BeforeToolCallResult.needsHitl("awaiting approval");
            }
            return BeforeToolCallResult.allow();
        });

        Map<String, Object> updates = node.execute(
                GraphState.create(toolTurn(
                        new ToolCallEntry("r1", "lookup", JsonNodeFactory.instance.objectNode()),
                        new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()))),
                new NodeContext("r1", "tr1", emitter));

        assertThat(lookupCalls.get()).isEqualTo(1);
        assertThat(saveCalls.get()).isZero();
        assertThat(updates.get(StateKeys.INTERRUPT)).isEqualTo(Boolean.TRUE);
        @SuppressWarnings("unchecked")
        List<ToolCallEntry> remaining = (List<ToolCallEntry>) updates.get(StateKeys.TOOL_CALLS);
        assertThat(remaining).extracting(ToolCallEntry::getToolName).containsExactly("save");
        assertThat(events).extracting(PiEvent::getType).containsExactly(
                PiEventType.TOOL_EXECUTION_START, PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL, PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START, PiEventType.MESSAGE_END,
                PiEventType.TOOL_EXECUTION_START, PiEventType.BEFORE_TOOL_CALL,
                PiEventType.SUSPENDED);
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs).anyMatch(m -> "tool".equalsIgnoreCase(m.getRole())
                && "r1".equals(m.getToolCallId()));
    }

    @Test
    void block_skips_execute_but_still_emits_after_end_message() {
        AtomicInteger handlerCalls = new AtomicInteger();
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("danger", (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "nope");
        });
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        Emitter emitter = recording(events, event -> BeforeToolCallResult.block("denied"));

        Map<String, Object> updates = node.execute(
                GraphState.create(toolTurn("c1", "danger")),
                new NodeContext("r1", "tr1", emitter));

        assertThat(handlerCalls.get()).isZero();
        assertThat(updates.get(StateKeys.INTERRUPT)).isNull();
        assertThat(events).extracting(PiEvent::getType).containsExactly(
                PiEventType.TOOL_EXECUTION_START,
                PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL,
                PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START,
                PiEventType.MESSAGE_END);
        ToolResult end = (ToolResult) events.get(3).getPayload();
        assertThat(end.isSuccess()).isFalse();
        assertThat(end.getErrorMessage()).contains("denied");
    }

    @Test
    void after_tool_call_reduce_can_replace_result() {
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "raw"));
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        Emitter emitter = new Emitter() {
            @Override
            public void emit(PiEvent event) {
                events.add(event);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T emit(PiEvent event, Class<T> resultType) {
                events.add(event);
                if (event.getType() == PiEventType.BEFORE_TOOL_CALL) {
                    return resultType.cast(BeforeToolCallResult.allow());
                }
                if (event.getType() == PiEventType.AFTER_TOOL_CALL) {
                    ToolResult original = (ToolResult) event.getPayload();
                    return resultType.cast(AfterToolCallResult.of(
                            ToolResult.ok(original.getCallId(), original.getToolName(), "rewritten")));
                }
                return null;
            }
        };

        node.execute(GraphState.create(toolTurn("c1", "lookup")),
                new NodeContext("r1", "tr1", emitter));

        ToolResult end = (ToolResult) events.get(3).getPayload();
        assertThat(end.getOutput()).isEqualTo("rewritten");
    }

    @Test
    void emitter_throw_still_writes_results() {
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "data"));

        AtomicInteger hits = new AtomicInteger();
        Emitter emitter = new Emitter() {
            @Override
            public void emit(PiEvent event) {
                hits.incrementAndGet();
                throw new RuntimeException("observer boom");
            }

            @Override
            public <T> T emit(PiEvent event, Class<T> resultType) {
                hits.incrementAndGet();
                throw new RuntimeException("observer boom");
            }
        };

        Map<String, Object> updates = new ToolNode(handlers).execute(
                GraphState.create(toolTurn("c1", "lookup")),
                new NodeContext("r1", "tr1", emitter));

        assertThat(hits.get()).isGreaterThanOrEqualTo(1);
        assertThat(updates.containsKey(StateKeys.MESSAGES)).isTrue();
        assertThat(updates.get(StateKeys.TOOL_CALLS)).isEqualTo(Collections.emptyList());
    }

    @Test
    void handler_interrupt_writesToolMessage_clearsCall_andSuspends() {
        AtomicInteger handlerCalls = new AtomicInteger();
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("ask_human", (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.interrupt(call.getId(), call.getToolName(),
                    "{\"question\":\"确认？\",\"options\":[{\"id\":\"ok\",\"label\":\"好\"}]}");
        });
        handlers.put("save", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "written"));
        ToolNode node = new ToolNode(handlers);

        List<PiEvent> events = new ArrayList<>();
        Map<String, Object> updates = node.execute(
                GraphState.create(toolTurn(
                        new ToolCallEntry("ah1", "ask_human", JsonNodeFactory.instance.objectNode()),
                        new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode()))),
                new NodeContext("run-ask", "tr1", recording(events)));

        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(updates.get(StateKeys.INTERRUPT)).isEqualTo(Boolean.TRUE);
        assertThat(updates.get(StateKeys.TOOL_POLICY_ROUTE))
                .isEqualTo(ToolPolicyExtension.ROUTE_NEEDS_HITL);
        @SuppressWarnings("unchecked")
        List<ToolCallEntry> remaining = (List<ToolCallEntry>) updates.get(StateKeys.TOOL_CALLS);
        // ask_human 已完成；仅后续未执行的 save 留在 remaining
        assertThat(remaining).extracting(ToolCallEntry::getId).containsExactly("w1");
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs).anyMatch(m -> "tool".equalsIgnoreCase(m.getRole())
                && "ah1".equals(m.getToolCallId())
                && m.getContent() != null && m.getContent().contains("确认？"));
        assertThat(events).extracting(PiEvent::getType).containsExactly(
                PiEventType.TOOL_EXECUTION_START,
                PiEventType.BEFORE_TOOL_CALL,
                PiEventType.AFTER_TOOL_CALL,
                PiEventType.TOOL_EXECUTION_END,
                PiEventType.MESSAGE_START,
                PiEventType.MESSAGE_END,
                PiEventType.SUSPENDED);
        ToolSuspendPayload suspend = (ToolSuspendPayload) events.get(6).getPayload();
        assertThat(suspend.getCall().getId()).isEqualTo("ah1");
        assertThat(suspend.getResult()).isNotNull();
        assertThat(suspend.getResult().isInterrupt()).isTrue();
    }

    private static Map<String, Object> toolTurn(String callId, String toolName) {
        return toolTurn(new ToolCallEntry(callId, toolName, JsonNodeFactory.instance.objectNode()));
    }

    private static Map<String, Object> toolTurn(ToolCallEntry... calls) {
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.TOOL_CALLS, Arrays.asList(calls));
        initial.put(StateKeys.MESSAGES, Collections.emptyList());
        return initial;
    }

    private static Emitter recording(List<PiEvent> events) {
        return recording(events, event -> BeforeToolCallResult.allow());
    }

    private static Emitter recording(List<PiEvent> events,
                                     Function<PiEvent, BeforeToolCallResult> before) {
        return new Emitter() {
            @Override
            public void emit(PiEvent event) {
                events.add(event);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T emit(PiEvent event, Class<T> resultType) {
                events.add(event);
                if (event.getType() == PiEventType.BEFORE_TOOL_CALL
                        && resultType == BeforeToolCallResult.class) {
                    return resultType.cast(before.apply(event));
                }
                return null;
            }
        };
    }
}

package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.extension.PiTestBus;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolAuditEvent;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.Tool;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;

/**
 * AC5：FORBIDDEN / WRITE HITL approve·deny / READ 直过。
 */
class ToolCatalogHitlIntegrationTest {

    @Test
    void forbidden_handlerNeverCalled_auditRecorded_andFailedToolResult() {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicInteger agentVisits = new AtomicInteger();
        List<ToolAuditEvent> events = Collections.synchronizedList(new ArrayList<>());

        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "should-not-run");
        };
        InMemoryToolCatalog policy = InMemoryToolCatalog.empty();

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "danger", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                Object raw = state.get(StateKeys.MESSAGES);
                assertThat(raw).isInstanceOf(List.class);
                @SuppressWarnings("unchecked")
                List<com.xmut.lims.pi.ai.message.Message> msgs =
                        (List<com.xmut.lims.pi.ai.message.Message>) raw;
                assertThat(msgs).anyMatch(m ->
                        "tool".equalsIgnoreCase(m.getRole())
                                && m.getContent() != null
                                && m.getContent().contains("registered"));
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        ConversationResult result = loop.run(TurnInput.withUser("try forbidden")
                .build(), PiTestBus.withPolicy(policy, events::add));

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isZero();
        assertThat(events).anyMatch(e -> e.getKind() == ToolAuditEvent.Kind.FORBIDDEN);
    }

    @Test
    void mixedReadWrite_perCall_readExecutes_writeSuspendsUntilApprove() {
        AtomicInteger readCalls = new AtomicInteger();
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger agentVisits = new AtomicInteger();

        InMemoryToolCatalog policy = new InMemoryToolCatalog(Arrays.asList(
                ToolTestSupport.tool("lookup", (call, ctx) -> {
                    readCalls.incrementAndGet();
                    return ToolResult.ok(call.getId(), call.getToolName(), "data");
                }),
                ToolTestSupport.tool("save", (call, ctx) -> {
                    writeCalls.incrementAndGet();
                    return ToolResult.ok(call.getId(), call.getToolName(), "written");
                })));

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Arrays.asList(
                        new ToolCallEntry("r1", "lookup", JsonNodeFactory.instance.objectNode()),
                        new ToolCallEntry("w1", "save", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("mixed-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("lookup and save")))
                .build(), bus);

        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(readCalls.get()).isZero();
        assertThat(writeCalls.get()).isZero();

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("mixed-run")
                .decision(ToolDecision.APPROVE)
                .build(), bus);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(readCalls.get()).isEqualTo(1);
        assertThat(writeCalls.get()).isEqualTo(1);
    }

    @Test
    void resume_withoutDecision_failClosed() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save",
                        (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "x"))));

        GraphNode agent = visitingAgent("save");
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("no-decision-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus);
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult bad = loop.resume(ResumeRequest.builder()
                .runId("no-decision-run")
                .build());
        assertThat(bad.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(bad.getFinalResponse()).containsIgnoringCase("decision");
    }

    @Test
    void write_firstCall_suspends_approve_executesOnce() {
        AtomicInteger handlerCalls = new AtomicInteger();
        List<ToolAuditEvent> events = Collections.synchronizedList(new ArrayList<>());

        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            assertThat(ctx.getRunId()).isEqualTo("write-run");
            return ToolResult.ok(call.getId(), call.getToolName(), "written");
        };
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save", handler)));

        GraphNode agent = visitingAgent("save");
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy, events::add);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("write-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save please")))
                .build(), bus);

        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(first.getFinalResponse()).contains("tools");
        assertThat(handlerCalls.get()).isZero();
        assertThat(events).anyMatch(e -> e.getKind() == ToolAuditEvent.Kind.SUSPEND);

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("write-run")
                .decision(ToolDecision.APPROVE)
                .build(), bus);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(events).anyMatch(e -> e.getKind() == ToolAuditEvent.Kind.APPROVE);
    }

    @Test
    void write_resume_paddedRunId_findsCheckpointAndApproves() {
        AtomicInteger handlerCalls = new AtomicInteger();
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save", (call, ctx) -> {
                    handlerCalls.incrementAndGet();
                    return ToolResult.ok(call.getId(), call.getToolName(), "written");
                })));

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("padded-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save please")))
                .build(), bus);
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("  padded-run  ")
                .decision(ToolDecision.APPROVE)
                .build(), bus);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(resumed.getRunId()).isEqualTo("padded-run");
        assertThat(handlerCalls.get()).isEqualTo(1);
    }

    @Test
    void write_resumeDeny_handlerZero_rejectResult() {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicInteger agentVisits = new AtomicInteger();
        List<ToolAuditEvent> events = Collections.synchronizedList(new ArrayList<>());

        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "nope");
        };
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("mutate", handler)));

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "mutate", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                Object raw = state.get(StateKeys.MESSAGES);
                assertThat(raw).isInstanceOf(List.class);
                @SuppressWarnings("unchecked")
                List<com.xmut.lims.pi.ai.message.Message> msgs =
                        (List<com.xmut.lims.pi.ai.message.Message>) raw;
                assertThat(msgs).anyMatch(m ->
                        "tool".equalsIgnoreCase(m.getRole())
                                && m.getContent() != null
                                && m.getContent().contains("denied")
                                && m.getContent().contains("not allowed"));
                updates.put(StateKeys.LLM_RESPONSE, "saw-deny");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy, events::add);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("deny-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("mutate")))
                .build(), bus);
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("deny-run")
                .approved(false)
                .humanInput("not allowed")
                .build(), bus);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(resumed.getFinalResponse()).isEqualTo("saw-deny");
        assertThat(handlerCalls.get()).isZero();
        assertThat(events).anyMatch(e -> e.getKind() == ToolAuditEvent.Kind.DENY);
    }

    @Test
    void read_doesNotSuspend_executesDirectly() {
        AtomicInteger handlerCalls = new AtomicInteger();
        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("lookup", (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "data");
        });
        InMemoryToolCatalog policy = ToolTestSupport.readConfig(handlers);

        GraphNode agent = visitingAgent("lookup");
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        ConversationResult result = loop.run(TurnInput.withUser("lookup")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isEqualTo(1);
    }

    @Test
    void read_emits_tool_call_then_tool_result() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("lookup",
                        (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "data"))));

        GraphNode agent = visitingAgent("lookup");
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        List<PiEvent> events = new ArrayList<>();
        com.xmut.lims.pi.agent.event.PiEventBus bus = new com.xmut.lims.pi.agent.event.DefaultPiEventBus();
        new com.xmut.lims.pi.agent.extension.ToolPolicyExtension(policy, false).register(bus);
        bus.subscribe(events::add);
        ConversationResult result = loop.run(TurnInput.withUser("lookup")
                .build(), bus);

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(events).extracting(PiEvent::getType)
                .containsExactly(
                        PiEventType.TOOL_EXECUTION_START,
                        PiEventType.BEFORE_TOOL_CALL,
                        PiEventType.AFTER_TOOL_CALL,
                        PiEventType.TOOL_EXECUTION_END,
                        PiEventType.MESSAGE_START,
                        PiEventType.MESSAGE_END);
        ToolCallEntry start = (ToolCallEntry) events.get(0).getPayload();
        ToolResult end = (ToolResult) events.get(3).getPayload();
        assertThat(start.getId()).isEqualTo("c1");
        assertThat(start.getToolName()).isEqualTo("lookup");
        assertThat(end.getCallId()).isEqualTo("c1");
        assertThat(end.getOutput()).isEqualTo("data");
    }

    @Test
    void write_hitl_emits_start_before_then_suspended() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save",
                        (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "x"))));

        GraphNode agent = visitingAgent("save");
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        List<PiEvent> events = new ArrayList<>();
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        bus.subscribe(events::add);
        ConversationResult first = loop.run(TurnInput.builder()
                .runId("hitl-order")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus);

        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(events).extracting(PiEvent::getType).containsExactly(
                PiEventType.TOOL_EXECUTION_START,
                PiEventType.BEFORE_TOOL_CALL,
                PiEventType.SUSPENDED);
    }

    @Test
    void availableTools_writtenFromPolicy() {
    }

    private static GraphNode visitingAgent(String toolName) {
        AtomicInteger visits = new AtomicInteger();
        return (state, ctx) -> {
            int visit = visits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", toolName, JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };
    }
}

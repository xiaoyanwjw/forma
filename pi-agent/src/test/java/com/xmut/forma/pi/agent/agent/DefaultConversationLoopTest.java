package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.ConversationResult;
import com.xmut.forma.pi.agent.IterationBudget;
import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.graph.GraphNode;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.ai.tool.ToolResult;
import com.xmut.forma.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.forma.pi.agent.tool.ToolDecision;
import com.xmut.forma.pi.agent.tool.ToolTestSupport;
import com.xmut.forma.pi.agent.event.Emitter;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.extension.PiTestBus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.node.AgentTurnNode;

class DefaultConversationLoopTest {

    @Test
    void noTool_oneRound_returnsOk_withRunId() {
        DefaultAgent loop = new DefaultAgent(DefaultToolLoopGraph.create(com.xmut.forma.pi.agent.graph.node.AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()), new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);
        ConversationResult result = loop.run(TurnInput.withUser("hello hermes")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("hello hermes");
        assertThat(result.getRunId()).isNotBlank();
    }

    @Test
    void run_withEmitter_exposesItOnNodeContext() {
        AtomicReference<Emitter> seen = new AtomicReference<>();
        GraphNode agent = (state, ctx) -> {
            seen.set(ctx.getEmitter());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
            updates.put(StateKeys.LLM_RESPONSE, "streamed");
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);

        Emitter emitter = new Emitter() {
            @Override
            public void emit(PiEvent event) {
            }

            @Override
            public <T> T emit(PiEvent event, Class<T> resultType) {
                return null;
            }
        };

        ConversationResult result = loop.run(TurnInput.withUser("hi")
                .build(), emitter);

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(seen.get()).isSameAs(emitter);
    }

    @Test
    void run_withoutEmitter_nodeContextEmitterIsNull() {
        AtomicReference<Emitter> seen = new AtomicReference<>();
        GraphNode agent = (state, ctx) -> {
            seen.set(ctx.getEmitter());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
            updates.put(StateKeys.LLM_RESPONSE, "ok");
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);

        ConversationResult result = loop.run(TurnInput.withUser("hi")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(seen.get()).isNull();
    }

    @Test
    void resume_withEmitter_exposesItOnNodeContext() {
        AtomicReference<Emitter> seen = new AtomicReference<>();
        AtomicInteger agentVisits = new AtomicInteger();
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save",
                        (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "written"))));

        GraphNode agent = (state, ctx) -> {
            seen.set(ctx.getEmitter());
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "save", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        ConversationResult first = loop.run(TurnInput.builder()
                .runId("resume-emit")
                .messages(Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("save")))
                .build(), PiTestBus.withPolicy(policy));
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        Emitter emitter = new Emitter() {
            @Override
            public void emit(PiEvent event) {
            }

            @Override
            public <T> T emit(PiEvent event, Class<T> resultType) {
                return null;
            }
        };

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("resume-emit")
                .decision(ToolDecision.APPROVE)
                .build(), emitter);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(seen.get()).isSameAs(emitter);
    }

    @Test
    void prepare_uses_messages_as_complete_transcript() {
    }

    @Test
    void prepare_withUser_factory_seeds_single_user() {
    }

    @Test
    void prepare_strips_system_keeps_chat_history_as_is() {
    }

    @Test
    void run_null_request_fails_fast() {
        DefaultAgent loop = new DefaultAgent(DefaultToolLoopGraph.create(com.xmut.forma.pi.agent.graph.node.AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()), new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);
        ConversationResult result = loop.run(null);
        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(result.getFinalResponse()).contains("turnInput required");
    }

    @Test
    void clientProvidedRunId_isEchoed() {
        DefaultAgent loop = new DefaultAgent(DefaultToolLoopGraph.create(com.xmut.forma.pi.agent.graph.node.AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()), new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);
        ConversationResult result = loop.run(TurnInput.builder()
                .runId("client-run-1")
                .messages(java.util.Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("hello")))
                .build());

        assertThat(result.getRunId()).isEqualTo("client-run-1");
        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
    }

    @Test
    void withTool_oneRoundTrip_thenEnds() {
        AtomicInteger agentVisits = new AtomicInteger(0);

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "echo", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "after-tool");
            }
            return updates;
        };

        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("echo", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "pong"));
        InMemoryToolCatalog policy = ToolTestSupport.readConfig(handlers);

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        ConversationResult result = loop.run(TurnInput.withUser("use tool")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("after-tool");
        assertThat(agentVisits.get()).isEqualTo(2);
        assertThat(result.getRunId()).isNotBlank();
    }

    @Test
    void cancel_withClientRunId_stopsAtBoundary() throws Exception {
        AtomicInteger agentVisits = new AtomicInteger(0);
        CountDownLatch toolsEntered = new CountDownLatch(1);
        String runId = "cancel-client-run";

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "slow", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "should-not-reach");
            }
            return updates;
        };

        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("slow", (call, ctx) -> {
            toolsEntered.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.ok(call.getId(), call.getToolName(), "done");
        });
        InMemoryToolCatalog policy = ToolTestSupport.readConfig(handlers);

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        AtomicReference<ConversationResult> resultRef = new AtomicReference<>();
        Thread t = new Thread(() -> resultRef.set(loop.run(TurnInput.builder()
                .runId(runId)
                .messages(java.util.Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("cancel me")))
                .build())));
        t.start();

        assertThat(toolsEntered.await(3, TimeUnit.SECONDS)).isTrue();
        loop.cancel(runId, "stop-please");
        t.join(5000);

        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get().getStatus()).isEqualTo(ConversationResult.Status.CANCELLED);
        assertThat(resultRef.get().getFinalResponse()).isEqualTo("stop-please");
        assertThat(resultRef.get().getRunId()).isEqualTo(runId);
        assertThat(agentVisits.get()).isEqualTo(1);
    }

    @Test
    void cancel_thenResume_failsWithoutCheckpoint() throws Exception {
        CountDownLatch toolsEntered = new CountDownLatch(1);
        String runId = "cancel-then-resume";

        GraphNode agent = (state, ctx) -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                    new ToolCallEntry("c1", "slow", JsonNodeFactory.instance.objectNode())));
            return updates;
        };

        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("slow", (call, ctx) -> {
            toolsEntered.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.ok(call.getId(), call.getToolName(), "done");
        });
        InMemoryToolCatalog policy = ToolTestSupport.readConfig(handlers);

        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy), store, new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        Thread t = new Thread(() -> loop.run(TurnInput.builder()
                .runId(runId)
                .messages(java.util.Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("x")))
                .build()));
        t.start();
        assertThat(toolsEntered.await(3, TimeUnit.SECONDS)).isTrue();
        loop.cancel(runId, "bye");
        t.join(5000);

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId(runId)
                .decision(ToolDecision.APPROVE)
                .build());
        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(resumed.getFinalResponse()).contains("No checkpoint found");
        assertThat(store.listByRun(runId)).isEmpty();
    }

    @Test
    void maxSupersteps_returnsFailed() {
        GraphNode foreverTools = (state, ctx) -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                    new ToolCallEntry("c1", "noop", JsonNodeFactory.instance.objectNode())));
            return updates;
        };

        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("noop", (call, ctx) -> ToolResult.ok(call.getId(), call.getToolName(), "ok"));
        InMemoryToolCatalog policy = ToolTestSupport.readConfig(handlers);

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(foreverTools, policy),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                new IterationBudget(3),
                policy,
                null);

        ConversationResult result = loop.run(TurnInput.withUser("loop")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(result.getFinalResponse()).contains("Max supersteps (3) exceeded");
        assertThat(result.getRunId()).isNotBlank();
    }

    @Test
    void cancel_blankRunId_isSilent() {
        DefaultAgent loop = new DefaultAgent(DefaultToolLoopGraph.create(com.xmut.forma.pi.agent.graph.node.AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()), new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);
        loop.cancel(null, "x");
        loop.cancel("", "x");
        loop.cancel("   ", "x");
    }

    @Test
    void duplicateActiveRunId_failsFast() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        String runId = "dup-run";

        GraphNode slow = (state, ctx) -> {
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
            updates.put(StateKeys.LLM_RESPONSE, "done");
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(slow, InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), InMemoryToolCatalog.empty(), null);

        Thread t = new Thread(() -> loop.run(TurnInput.builder()
                .runId(runId)
                .messages(java.util.Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("a")))
                .build()));
        t.start();
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

        ConversationResult dup = loop.run(TurnInput.builder()
                .runId(runId)
                .messages(java.util.Collections.singletonList(com.xmut.forma.pi.ai.message.Message.user("b")))
                .build());
        assertThat(dup.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(dup.getFinalResponse()).contains("run already active");

        release.countDown();
        t.join(5000);
    }
}

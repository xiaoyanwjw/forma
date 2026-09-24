package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.SharedJsonCheckpointStore;
import com.xmut.lims.pi.agent.extension.PiTestBus;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC2/AC3/AC5：跨实例 loadLatest+resume、confirmRequestId 幂等、终态清理。
 */
class CheckpointPersistenceHitlTest {

    @Test
    void crossInstance_podASuspend_podBResumeApprove_handlerOnce() {
        AtomicInteger handlerCalls = new AtomicInteger();
        DefaultToolConfig policy = writeConfig(handlerCalls);
        SharedJsonCheckpointStore storeA = new SharedJsonCheckpointStore();
        SharedJsonCheckpointStore storeB = storeA.newPeer();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();

        GraphNode agent = stateAwareAgent("save");
        DefaultAgent loopA = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy), storeA, idem, null, policy, null);
        // Pod B：新 JVM 上的新图实例，但共享 Redis 态（SharedJson + 同一 idem）
        DefaultAgent loopB = new DefaultAgent(
                DefaultToolLoopGraph.create(stateAwareAgent("save"), policy), storeB, idem, null, policy, null);

        ConversationResult first = loopA.run(TurnInput.builder()
                .runId("cross-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), PiTestBus.withPolicy(policy));
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(storeB.loadLatest("cross-run")).isPresent();
        assertThat(handlerCalls.get()).isZero();

        ConversationResult resumed = loopB.resume(ResumeRequest.builder()
                .runId("cross-run")
                .decision(ToolDecision.APPROVE)
                .confirmRequestId("confirm-1")
                .build(), PiTestBus.withPolicy(policy));

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(storeB.listByRun("cross-run")).isEmpty();
    }

    @Test
    void doubleApprove_sameConfirmRequestId_handlerStillOnce() {
        AtomicInteger handlerCalls = new AtomicInteger();
        DefaultToolConfig policy = writeConfig(handlerCalls);
        SharedJsonCheckpointStore store = new SharedJsonCheckpointStore();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy), store, idem, null, policy, null);

        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("idem-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ResumeRequest resume = ResumeRequest.builder()
                .runId("idem-run")
                .decision(ToolDecision.APPROVE)
                .confirmRequestId("same-confirm")
                .build();

        ConversationResult first = loop.resume(resume, bus);
        ConversationResult second = loop.resume(resume, bus);

        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(second.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(second.getFinalResponse()).isEqualTo(first.getFinalResponse());
        assertThat(handlerCalls.get()).isEqualTo(1);
    }

    @Test
    void suspended_keepsCheckpoint_terminal_deletes() {
        DefaultToolConfig policy = writeConfig(new AtomicInteger());
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(),
                null,
                policy,
                null);

        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("keep-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(store.listByRun("keep-run")).isNotEmpty();

        assertThat(loop.resume(ResumeRequest.builder()
                .runId("keep-run")
                .decision(ToolDecision.APPROVE)
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(store.listByRun("keep-run")).isEmpty();
    }

    @Test
    void doubleDeny_sameConfirmRequestId_idempotent() {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicInteger agentVisits = new AtomicInteger();
        DefaultToolConfig policy = writeConfig(handlerCalls);

        GraphNode agent = (state, ctx) -> {
            int visit = agentVisits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "save", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "denied-ok");
            }
            return updates;
        };

        SharedJsonCheckpointStore store = new SharedJsonCheckpointStore();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy), store, idem, null, policy, null);

        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("deny-idem")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ResumeRequest deny = ResumeRequest.builder()
                .runId("deny-idem")
                .decision(ToolDecision.DENY)
                .confirmRequestId("deny-1")
                .build();

        ConversationResult first = loop.resume(deny, bus);
        ConversationResult second = loop.resume(deny, bus);
        assertThat(first.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(second.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isZero();
    }

    private static DefaultToolConfig writeConfig(AtomicInteger handlerCalls) {
        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "written");
        };
        return new DefaultToolConfig(Collections.singletonList(
                ToolTestSupport.registration("save", ToolLevel.WRITE, handler)));
    }

    private static GraphNode stateAwareAgent(String toolName) {
        return (state, ctx) -> {
            Map<String, Object> updates = new HashMap<>();
            Object raw = state.get(StateKeys.MESSAGES);
            boolean hasToolMsg = false;
            if (raw instanceof java.util.List) {
                for (Object item : (java.util.List<?>) raw) {
                    if (item instanceof com.xmut.lims.pi.ai.message.Message
                            && "tool".equalsIgnoreCase(
                            ((com.xmut.lims.pi.ai.message.Message) item).getRole())) {
                        hasToolMsg = true;
                        break;
                    }
                }
            }
            if (hasToolMsg) {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", toolName, JsonNodeFactory.instance.objectNode())));
            }
            return updates;
        };
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

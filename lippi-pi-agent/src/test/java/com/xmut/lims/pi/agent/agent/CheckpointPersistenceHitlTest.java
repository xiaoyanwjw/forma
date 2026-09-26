package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.SharedJsonCheckpointStore;
import com.xmut.lims.pi.agent.extension.PiTestBus;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import com.xmut.lims.pi.agent.IterationBudget;

/**
 * AC2/AC3/AC5：跨实例 loadLatest+resume、confirmRequestId 幂等、终态清理。
 */
class CheckpointPersistenceHitlTest {

    @Test
    void crossInstance_podASuspend_podBResumeApprove_handlerOnce() {
        AtomicInteger handlerCalls = new AtomicInteger();
        InMemoryToolCatalog policy = writeConfig(handlerCalls);
        SharedJsonCheckpointStore storeA = new SharedJsonCheckpointStore();
        SharedJsonCheckpointStore storeB = storeA.newPeer();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();

        GraphNode agent = stateAwareAgent("save");
        DefaultAgent loopA = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy), storeA, idem, new IterationBudget(25), policy, null);
        // Pod B：新 JVM 上的新图实例，但共享 Redis 态（SharedJson + 同一 idem）
        DefaultAgent loopB = new DefaultAgent(
                DefaultToolLoopGraph.create(stateAwareAgent("save"), policy), storeB, idem, new IterationBudget(25), policy, null);

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
        InMemoryToolCatalog policy = writeConfig(handlerCalls);
        SharedJsonCheckpointStore store = new SharedJsonCheckpointStore();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy), store, idem, new IterationBudget(25), policy, null);

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
        InMemoryToolCatalog policy = writeConfig(new AtomicInteger());
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

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
        InMemoryToolCatalog policy = writeConfig(handlerCalls);

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
                DefaultToolLoopGraph.create(agent, policy), store, idem, new IterationBudget(25), policy, null);

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

    @Test
    void toolResultResume_injectsResult_handlerNeverRuns() {
        AtomicInteger handlerCalls = new AtomicInteger();
        InMemoryToolCatalog policy = writeConfig(handlerCalls);
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(stateAwareAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);

        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("tool-result-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(handlerCalls.get()).isZero();

        ConversationResult resumed = loop.resume(ResumeRequest.builder()
                .runId("tool-result-run")
                .toolCallId("c1")
                .humanInput("user-chose-option-a")
                .confirmRequestId("tr-1")
                .build(), bus);

        assertThat(resumed.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isZero();
        assertThat(resumed.getMessages()).anyMatch(m ->
                "tool".equalsIgnoreCase(m.getRole())
                        && "c1".equals(m.getToolCallId())
                        && "user-chose-option-a".equals(m.getContent()));
        assertThat(store.listByRun("tool-result-run")).isEmpty();
    }

    @Test
    void resume_missingBothModes_failsClosed() {
        InMemoryToolCatalog policy = writeConfig(new AtomicInteger());
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("fail-closed")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult failed = loop.resume(ResumeRequest.builder()
                .runId("fail-closed")
                .build(), bus);
        assertThat(failed.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(failed.getFinalResponse()).contains("toolCallId");
    }

    @Test
    void resume_toolCallIdAndDecision_mutuallyExclusive_failsClosed() {
        InMemoryToolCatalog policy = writeConfig(new AtomicInteger());
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("mutex-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult failed = loop.resume(ResumeRequest.builder()
                .runId("mutex-run")
                .toolCallId("c1")
                .humanInput("answer")
                .decision(ToolDecision.APPROVE)
                .build(), bus);
        assertThat(failed.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(failed.getFinalResponse()).containsIgnoringCase("mutually exclusive");
        assertThat(store.loadLatest("mutex-run")).isPresent();
    }

    @Test
    void resume_unknownToolCallId_failsClosed_noInject() {
        AtomicInteger handlerCalls = new AtomicInteger();
        InMemoryToolCatalog policy = writeConfig(handlerCalls);
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(stateAwareAgent("save"), policy),
                store,
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);
        assertThat(loop.run(TurnInput.builder()
                .runId("unknown-id-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult failed = loop.resume(ResumeRequest.builder()
                .runId("unknown-id-run")
                .toolCallId("not-pending")
                .humanInput("should-not-inject")
                .build(), bus);
        assertThat(failed.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(failed.getFinalResponse()).contains("toolCallId");
        assertThat(failed.getFinalResponse()).containsIgnoringCase("unknown");
        assertThat(handlerCalls.get()).isZero();
        assertThat(store.loadLatest("unknown-id-run")).isPresent();
        @SuppressWarnings("unchecked")
        java.util.List<com.xmut.lims.pi.ai.message.Message> msgs =
                (java.util.List<com.xmut.lims.pi.ai.message.Message>) store.loadLatest("unknown-id-run")
                        .get().getState().get(StateKeys.MESSAGES);
        assertThat(msgs).noneMatch(m ->
                "tool".equalsIgnoreCase(m.getRole())
                        && "should-not-inject".equals(m.getContent()));
    }

    /** complete(null)：resume 抛错 → store.abandon；同 confirmId 可再 claim。 */
    @Test
    void resume_runtimeException_abandonsConfirm_allowsReclaim() {
        InMemoryToolCatalog policy = writeConfig(new AtomicInteger());
        InMemoryCheckpointer inner = new InMemoryCheckpointer();
        AtomicInteger boomOnLoad = new AtomicInteger(0);
        Checkpointer store = new Checkpointer() {
            @Override
            public void save(Checkpoint checkpoint) {
                inner.save(checkpoint);
            }

            @Override
            public Optional<Checkpoint> loadLatest(String runId) {
                if (boomOnLoad.get() > 0) {
                    throw new IllegalStateException("cp-load-boom");
                }
                return inner.loadLatest(runId);
            }

            @Override
            public Optional<Checkpoint> load(String runId, String checkpointId) {
                return inner.load(runId, checkpointId);
            }

            @Override
            public List<Checkpoint> listByRun(String runId) {
                return inner.listByRun(runId);
            }

            @Override
            public void deleteByRun(String runId) {
                inner.deleteByRun(runId);
            }
        };
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(visitingAgent("save"), policy),
                store, idem, new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);

        assertThat(loop.run(TurnInput.builder()
                .runId("ex-run")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        boomOnLoad.incrementAndGet();
        ConversationResult failed = loop.resume(ResumeRequest.builder()
                .runId("ex-run")
                .decision(ToolDecision.APPROVE)
                .confirmRequestId("confirm-ex")
                .build(), bus);
        assertThat(failed.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(failed.getFinalResponse()).contains("cp-load-boom");
        assertThat(idem.claim("ex-run", "confirm-ex").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
    }

    /** 矩阵「再挂起」：resume 后又 needsHitl → CP 保留；同 confirmId 经 abandon 可再 claim。 */
    @Test
    void resume_againSuspends_keepsCheckpoint_andAbandonsConfirm() {
        AtomicInteger handlerCalls = new AtomicInteger();
        InMemoryToolCatalog policy = writeConfig(handlerCalls);
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        InMemoryResumeIdempotencyStore idem = new InMemoryResumeIdempotencyStore();

        AtomicInteger visits = new AtomicInteger();
        GraphNode agent = (state, ctx) -> {
            int visit = visits.incrementAndGet();
            Map<String, Object> updates = new HashMap<>();
            if (visit == 1) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c1", "save", JsonNodeFactory.instance.objectNode())));
            } else if (visit == 2) {
                updates.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                        new ToolCallEntry("c2", "save", JsonNodeFactory.instance.objectNode())));
            } else {
                updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                updates.put(StateKeys.LLM_RESPONSE, "done");
            }
            return updates;
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(agent, policy),
                store, idem, new IterationBudget(25), policy, null);
        com.xmut.lims.pi.agent.event.PiEventBus bus = PiTestBus.withPolicy(policy);

        assertThat(loop.run(TurnInput.builder()
                .runId("re-suspend")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("save")))
                .build(), bus).getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);

        ConversationResult mid = loop.resume(ResumeRequest.builder()
                .runId("re-suspend")
                .decision(ToolDecision.APPROVE)
                .confirmRequestId("confirm-re")
                .build(), bus);
        assertThat(mid.getStatus()).isEqualTo(ConversationResult.Status.SUSPENDED);
        assertThat(store.loadLatest("re-suspend")).isPresent();
        assertThat(handlerCalls.get()).isEqualTo(1);

        ConversationResult done = loop.resume(ResumeRequest.builder()
                .runId("re-suspend")
                .decision(ToolDecision.APPROVE)
                .confirmRequestId("confirm-re")
                .build(), bus);
        assertThat(done.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(handlerCalls.get()).isEqualTo(2);
        assertThat(store.listByRun("re-suspend")).isEmpty();
    }

    private static InMemoryToolCatalog writeConfig(AtomicInteger handlerCalls) {
        ToolHandler handler = (call, ctx) -> {
            handlerCalls.incrementAndGet();
            return ToolResult.ok(call.getId(), call.getToolName(), "written");
        };
        return new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("save", handler)));
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

package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GraphExecutorTest {

    private final RunnableConfig runConfig = RunnableConfig.of("run-1", "trace-1");

    @Test
    void twoNodeLinearGraph_executesSuccessfully() {
        StateGraph graph = new StateGraph()
                .addNode("first", (state, ctx) -> Collections.singletonMap("step1", "done"))
                .addNode("second", (state, ctx) -> Collections.singletonMap("step2", "done"))
                .addEdge(StateGraph.START, "first")
                .addEdge("first", "second")
                .addEdge("second", StateGraph.END);

        GraphOutcome outcome = graph.compile().invoke(Collections.singletonMap("input", "hello"), runConfig);

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.getFinalState().get("step1", String.class)).isEqualTo("done");
        assertThat(outcome.getFinalState().get("step2", String.class)).isEqualTo("done");
        assertThat(outcome.getFinalState().get("input", String.class)).isEqualTo("hello");
    }

    @Test
    void conditionalBranch_routesToCorrectNode() {
        Map<String, String> pathMap = new HashMap<>();
        pathMap.put("positive", "happy");
        pathMap.put("negative", "sad");

        StateGraph graph = new StateGraph()
                .addNode("router", (state, ctx) -> Collections.singletonMap("mood", "positive"))
                .addNode("happy", (state, ctx) -> Collections.singletonMap("result", "smile"))
                .addNode("sad", (state, ctx) -> Collections.singletonMap("result", "cry"))
                .addEdge(StateGraph.START, "router")
                .addConditionalEdges("router", s -> s.get("mood", String.class), pathMap)
                .addEdge("happy", StateGraph.END)
                .addEdge("sad", StateGraph.END);

        GraphOutcome outcome = graph.compile().invoke(Collections.emptyMap(), runConfig);

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.getFinalState().get("result", String.class)).isEqualTo("smile");
    }

    @Test
    void loop_stoppedByMaxSupersteps() {
        Map<String, String> pathMap = new HashMap<>();
        pathMap.put("continue", "looper");
        pathMap.put("done", StateGraph.END);

        StateGraph graph = new StateGraph()
                .addNode("looper", (state, ctx) -> {
                    Integer count = state.get("count", Integer.class);
                    int next = (count != null ? count : 0) + 1;
                    return Collections.singletonMap("count", next);
                })
                .addEdge(StateGraph.START, "looper")
                .addConditionalEdges("looper", s -> {
                    Integer c = s.get("count", Integer.class);
                    return (c != null && c >= 100) ? "done" : "continue";
                }, pathMap);

        CompileConfig config = CompileConfig.builder().maxSupersteps(5).build();
        GraphOutcome outcome = graph.compile(config).invoke(Collections.emptyMap(), runConfig);

        assertThat(outcome.isFailed()).isTrue();
        assertThat(outcome.getErrorMessage()).contains("Max supersteps (5) exceeded");
    }

    @Test
    void cancel_stopsAtSuperstepBoundary() throws Exception {
        AtomicInteger secondEntered = new AtomicInteger(0);
        AtomicBoolean firstStarted = new AtomicBoolean(false);
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        AtomicReference<String> cancelReason = new AtomicReference<>();

        StateGraph graph = new StateGraph()
                .addNode("first", (state, ctx) -> {
                    firstStarted.set(true);
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return Collections.singletonMap("first", true);
                })
                .addNode("second", (state, ctx) -> {
                    secondEntered.incrementAndGet();
                    return Collections.singletonMap("second", true);
                })
                .addEdge(StateGraph.START, "first")
                .addEdge("first", "second")
                .addEdge("second", StateGraph.END);

        RunnableConfig cfg = RunnableConfig.builder()
                .runId("cancel-run")
                .traceId("tr1")
                .cancelSignal(cancelFlag::get)
                .cancelReason(cancelReason)
                .build();

        AtomicReference<GraphOutcome> outcomeRef = new AtomicReference<>();
        Thread t = new Thread(() -> outcomeRef.set(graph.compile().invoke(Collections.emptyMap(), cfg)));
        t.start();

        while (!firstStarted.get()) {
            Thread.sleep(10);
        }
        cancelFlag.set(true);
        cancelReason.set("user-cancel");
        t.join(5000);

        assertThat(outcomeRef.get()).isNotNull();
        assertThat(outcomeRef.get().isCancelled()).isTrue();
        assertThat(outcomeRef.get().getCancelReason()).isEqualTo("user-cancel");
        assertThat(secondEntered.get()).isZero();
    }

    @Test
    void overallTimeout_failsAtBoundary() {
        StateGraph graph = new StateGraph()
                .addNode("slow", (state, ctx) -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return Collections.singletonMap("x", 1);
                })
                .addNode("next", (state, ctx) -> Collections.singletonMap("y", 2))
                .addEdge(StateGraph.START, "slow")
                .addEdge("slow", "next")
                .addEdge("next", StateGraph.END);

        CompileConfig config = CompileConfig.builder()
                .overallTimeout(Duration.ofMillis(50))
                .build();

        GraphOutcome outcome = graph.compile(config).invoke(Collections.emptyMap(), runConfig);

        assertThat(outcome.isFailed()).isTrue();
        assertThat(outcome.getErrorMessage()).contains("Overall timeout");
    }

    @Test
    void interruptBefore_suspendsAndResume_doesNotBurnExtraStep() {
        InMemoryCheckpointer store = new InMemoryCheckpointer();

        StateGraph graph = new StateGraph()
                .addNode("prepare", (state, ctx) -> Collections.singletonMap("prepared", true))
                .addNode("confirm", (state, ctx) -> Collections.singletonMap("confirmed", true))
                .addEdge(StateGraph.START, "prepare")
                .addEdge("prepare", "confirm")
                .addEdge("confirm", StateGraph.END);

        CompileConfig config = CompileConfig.builder()
                .checkpointer(store)
                .interruptBefore(Collections.singletonList("confirm"))
                .maxSupersteps(2)
                .build();

        CompiledGraph compiled = graph.compile(config);
        GraphOutcome outcome = compiled.invoke(Collections.emptyMap(), runConfig);

        assertThat(outcome.isSuspended()).isTrue();
        assertThat(outcome.getSuspendedNode()).isEqualTo("confirm");

        GraphOutcome resumed = compiled.resume(Collections.singletonMap("humanApproval", "yes"), runConfig);
        assertThat(resumed.isSuccess()).isTrue();
        assertThat(resumed.getFinalState().get("confirmed", Boolean.class)).isTrue();
        assertThat(resumed.getFinalState().get("humanApproval", String.class)).isEqualTo("yes");
    }

    @Test
    void nodeException_returnsFailed() {
        StateGraph graph = new StateGraph()
                .addNode("bomb", (state, ctx) -> {
                    throw new RuntimeException("kaboom");
                })
                .addEdge(StateGraph.START, "bomb")
                .addEdge("bomb", StateGraph.END);

        GraphOutcome outcome = graph.compile().invoke(Collections.emptyMap(), runConfig);

        assertThat(outcome.isFailed()).isTrue();
        assertThat(outcome.getErrorMessage()).contains("kaboom");
        assertThat(outcome.getCause()).isNotNull();
    }

    @Test
    void nodeInterrupt_suspendsAndResume_rerunsSameNode() {
        InMemoryCheckpointer store = new InMemoryCheckpointer();
        AtomicInteger visits = new AtomicInteger();

        StateGraph graph = new StateGraph()
                .addNode("gate", (state, ctx) -> {
                    int n = visits.incrementAndGet();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("visits", n);
                    if (state.get("approved") == null) {
                        updates.put(StateKeys.INTERRUPT, Boolean.TRUE);
                    }
                    return updates;
                })
                .addEdge(StateGraph.START, "gate")
                .addEdge("gate", StateGraph.END);

        CompiledGraph compiled = graph.compile(CompileConfig.builder()
                .checkpointer(store)
                .maxSupersteps(4)
                .build());

        GraphOutcome first = compiled.invoke(Collections.emptyMap(), runConfig);
        assertThat(first.isSuspended()).isTrue();
        assertThat(first.getSuspendedNode()).isEqualTo("gate");
        assertThat(visits.get()).isEqualTo(1);
        assertThat(first.getFinalState().get(StateKeys.INTERRUPT)).isNull();

        GraphOutcome resumed = compiled.resume(Collections.singletonMap("approved", Boolean.TRUE), runConfig);
        assertThat(resumed.isSuccess()).isTrue();
        assertThat(visits.get()).isEqualTo(2);
        assertThat(resumed.getFinalState().get("visits", Integer.class)).isEqualTo(2);
        assertThat(resumed.getFinalState().get("approved", Boolean.class)).isTrue();
    }
}

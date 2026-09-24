package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 串行超步执行引擎（包可见）。
 *
 * <p>在每个超步边界检查：cancel、overallTimeout、maxSupersteps（对齐 NFR9；补齐 agent 引擎缺口）。
 */
final class GraphExecutor {

    private final CompiledGraph graph;
    private final CompileConfig config;

    GraphExecutor(CompiledGraph graph, CompileConfig config) {
        this.graph = graph;
        this.config = config;
    }

    GraphOutcome execute(Map<String, Object> input, RunnableConfig runnableConfig) {
        if (Objects.isNull(runnableConfig)) {
            return GraphOutcome.failed("runConfig required", null);
        }

        GraphState state = GraphState.create(input);
        Instant deadline = resolveDeadline();
        return executeFromNode(state, graph.getEntryPoint(), 0, runnableConfig, deadline, false);
    }

    GraphOutcome resume(Map<String, Object> input, RunnableConfig runnableConfig) {
        if (Objects.isNull(runnableConfig)) {
            return GraphOutcome.failed("runConfig required", null);
        }

        Checkpointer checkpointer = config.getCheckpointer();
        if (checkpointer == null) {
            return GraphOutcome.failed("No checkpointer configured", null);
        }
        Checkpoint latest = checkpointer.loadLatest(runnableConfig.getRunId()).orElse(null);
        if (Objects.isNull(latest)) {
            return GraphOutcome.failed("No checkpoint found for runId=" + runnableConfig.getRunId(), null);
        }

        GraphState state = latest.getState();
        if (!CollectionUtils.isEmpty(input)) {
            state = state.withUpdate(input);
        }

        String currentNode = latest.getCurrentNode();
        int step = latest.getStep();
        Instant deadline = resolveDeadline();

        try {
            if (config.getInterruptBefore().contains(currentNode)) {
                // interruptBefore：节点尚未执行，沿用同一 step 重入
                return executeFromNode(state, currentNode, step, runnableConfig, deadline, true);
            }
            if (config.getInterruptAfter().contains(currentNode)) {
                // interruptAfter：节点已完成，推进到下一节点并消耗下一超步编号
                String nextNode = resolveNext(currentNode, state);
                return executeFromNode(state, nextNode, step + 1, runnableConfig, deadline, false);
            }
            return executeFromNode(state, currentNode, step, runnableConfig, deadline, false);
        } catch (GraphExecutionException e) {
            return GraphOutcome.failed(e.getMessage(), e);
        }
    }

    private GraphOutcome executeFromNode(GraphState state, String currentNode, int step,
                                         RunnableConfig runConfig, Instant deadline,
                                         boolean skipFirstInterrupt) {
        boolean firstIteration = skipFirstInterrupt;
        while (!StateGraph.END.equals(currentNode)) {
            GraphOutcome beforeOutcome = beforeExecute(step, runConfig, deadline);
            if (beforeOutcome != null) {
                return beforeOutcome;
            }

            if (!firstIteration && config.getInterruptBefore().contains(currentNode)) {
                return suspendWithCheckpoint(runConfig, step, currentNode, state);
            }
            firstIteration = false;

            GraphNode node = graph.getNodes().get(currentNode);
            if (node == null) {
                return GraphOutcome.failed("Unknown node '" + currentNode + "'", null);
            }

            Map<String, Object> updates;
            try {
                NodeContext ctx = new NodeContext(
                        runConfig.getRunId(),
                        runConfig.getTraceId(),
                        runConfig.getEmitter());
                updates = node.execute(state, ctx);
            } catch (Exception e) {
                return GraphOutcome.failed("Node '" + currentNode + "' threw exception: " + e.getMessage(), e);
            }

            boolean interrupt = false;
            if (updates != null && !updates.isEmpty()) {
                if (Boolean.TRUE.equals(updates.get(StateKeys.INTERRUPT))) {
                    interrupt = true;
                    Map<String, Object> applied = new HashMap<>(updates);
                    applied.remove(StateKeys.INTERRUPT);
                    if (!applied.isEmpty()) {
                        state = state.withUpdate(applied);
                    }
                } else {
                    state = state.withUpdate(updates);
                }
            }

            if (interrupt) {
                return suspendWithCheckpoint(runConfig, step, currentNode, state);
            }

            if (config.getInterruptAfter().contains(currentNode)) {
                return suspendWithCheckpoint(runConfig, step, currentNode, state);
            }

            // 仅在 interrupt 钩子处持久化；避免每节点 save 造成进程级泄漏（Redis 见 51-6）
            GraphOutcome postOutcome = postExecute(runConfig, deadline);
            if (postOutcome != null) {
                return postOutcome;
            }

            try {
                currentNode = resolveNext(currentNode, state);
            } catch (Exception e) {
                return GraphOutcome.failed(
                        e.getMessage() != null ? e.getMessage() : "resolveNext failed", e);
            }
            step++;
        }

        return GraphOutcome.success(state);
    }

    private GraphOutcome beforeExecute(int step, RunnableConfig runConfig, Instant deadline) {
        GraphOutcome cancelOrTimeout = postExecute(runConfig, deadline);
        if (cancelOrTimeout != null) {
            return cancelOrTimeout;
        }
        if (step >= config.getMaxSupersteps()) {
            return GraphOutcome.failed(
                    "Max supersteps (" + config.getMaxSupersteps() + ") exceeded", null);
        }
        return null;
    }

    private GraphOutcome postExecute(RunnableConfig runConfig, Instant deadline) {
        if (runConfig != null && runConfig.isCancelRequested()) {
            String reason = runConfig.getCancelReason();
            return GraphOutcome.cancelled(reason != null ? reason : "cancelled");
        }
        if (deadline != null && Instant.now().isAfter(deadline)) {
            Duration timeout = config.getOverallTimeout();
            return GraphOutcome.failed(
                    "Overall timeout (" + (timeout != null ? timeout : "unknown") + ") exceeded", null);
        }
        return null;
    }

    private Instant resolveDeadline() {
        Duration timeout = config.getOverallTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return null;
        }
        try {
            return Instant.now().plus(timeout);
        } catch (ArithmeticException | java.time.DateTimeException e) {
            return Instant.MAX;
        }
    }

    private String resolveNext(String currentNode, GraphState state) {
        DirectEdge direct = graph.getDirectRoutes().get(currentNode);
        if (direct != null) {
            return direct.getTo();
        }
        ConditionalEdge conditional = graph.getConditionalRoutes().get(currentNode);
        if (conditional != null) {
            return conditional.resolve(state);
        }
        throw new GraphExecutionException("No outgoing edge from node '" + currentNode + "'");
    }

    private GraphOutcome suspendWithCheckpoint(RunnableConfig runConfig, int step, String node,
                                               GraphState state) {
        try {
            Checkpoint cp = checkpoint(runConfig, step, node, state);
            return GraphOutcome.suspended(node, cp);
        } catch (RuntimeException e) {
            return GraphOutcome.failed(
                    "Failed to persist checkpoint: "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    e);
        }
    }

    private Checkpoint checkpoint(RunnableConfig runConfig, int step, String node, GraphState state) {
        Checkpoint cp = new Checkpoint(
                UUID.randomUUID().toString(),
                runConfig.getRunId(),
                step,
                node,
                state,
                Instant.now(),
                runConfig.getMetadata());

        Optional.ofNullable(config.getCheckpointer()).ifPresent(checkpointer -> checkpointer.save(cp));

        return cp;
    }
}

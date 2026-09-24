package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;

import java.util.Map;

/**
 * 不可变编译后图；{@link #invoke} / {@link #resume} 为 ConversationLoop 唯一控制流入口。
 */
public final class CompiledGraph {

    private final Map<String, GraphNode> nodes;
    private final Map<String, DirectEdge> directRoutes;
    private final Map<String, ConditionalEdge> conditionalRoutes;
    private final String entryPoint;
    private final CompileConfig config;

    CompiledGraph(Map<String, GraphNode> nodes,
                  Map<String, DirectEdge> directRoutes,
                  Map<String, ConditionalEdge> conditionalRoutes,
                  String entryPoint,
                  CompileConfig config) {
        this.nodes = nodes;
        this.directRoutes = directRoutes;
        this.conditionalRoutes = conditionalRoutes;
        this.entryPoint = entryPoint;
        this.config = config;
    }

    /**
     * 同步执行到终态。
     */
    public GraphOutcome invoke(Map<String, Object> input, RunnableConfig runnableConfig) {
        return new GraphExecutor(this, config).execute(input, runnableConfig);
    }

    /**
     * 从最新 checkpoint 恢复；humanInput 合并进 state。
     */
    public GraphOutcome resume(Map<String, Object> input, RunnableConfig runnableConfig) {
        Checkpointer store = config.getCheckpointer();
        if (store == null) {
            throw new GraphExecutionException("Cannot resume without a CheckpointStore configured");
        }
        return new GraphExecutor(this, config).resume(input, runnableConfig);
    }

    Map<String, GraphNode> getNodes() {
        return nodes;
    }

    Map<String, DirectEdge> getDirectRoutes() {
        return directRoutes;
    }

    Map<String, ConditionalEdge> getConditionalRoutes() {
        return conditionalRoutes;
    }

    String getEntryPoint() {
        return entryPoint;
    }

    CompileConfig getConfig() {
        return config;
    }
}

package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.graph.EdgeCondition;
import com.xmut.lims.pi.agent.graph.GraphNode;
import com.xmut.lims.pi.agent.graph.StateGraph;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.node.AgentTurnNode;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.graph.node.ToolNode;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认 Tool-loop 图工厂。
 * 功能描述：构建 START → agent ⇄ tools → END 拓扑。
 */
public final class DefaultToolLoopGraph {

    public static final String NODE_AGENT = "agent";
    public static final String NODE_TOOLS = "tools";

    private DefaultToolLoopGraph() {
    }

    /** 生产装配；四参均必填。 */
    public static StateGraph build(ModelProvider modelProvider,
                                   PromptBuilder promptBuilder,
                                   ToolConfig toolConfig,
                                   ContextCompressor compressor) {
        return create(
                new AgentTurnNode(modelProvider, promptBuilder, compressor),
                toolConfig);
    }

    /** 自定义 agent 节点（HITL / 拓扑测）；两参均必填。 */
    public static StateGraph create(GraphNode agentNode, ToolConfig toolConfig) {
        Objects.requireNonNull(agentNode, "agentNode");
        Objects.requireNonNull(toolConfig, "toolConfig");

        ToolNode toolNode = new ToolNode(handlers(toolConfig));

        Map<String, String> agentPaths = new HashMap<>();
        agentPaths.put("tools", NODE_TOOLS);
        agentPaths.put("end", StateGraph.END);

        return new StateGraph()
                .addNode(NODE_AGENT, agentNode)
                .addNode(NODE_TOOLS, toolNode)
                .addEdge(StateGraph.START, NODE_AGENT)
                .addConditionalEdges(NODE_AGENT, hasToolCalls(), agentPaths)
                .addEdge(NODE_TOOLS, NODE_AGENT);
    }

    public static EdgeCondition hasToolCalls() {
        return state -> {
            Object raw = state.get(StateKeys.TOOL_CALLS);
            if (!(raw instanceof List)) {
                return "end";
            }
            List<?> calls = (List<?>) raw;
            return !calls.isEmpty() ? "tools" : "end";
        };
    }

    static Map<String, ToolHandler> handlers(ToolConfig config) {
        if (config instanceof DefaultToolConfig) {
            return ((DefaultToolConfig) config).handlers();
        }
        Map<String, ToolHandler> handlers = new LinkedHashMap<>();
        for (ToolSchema schema : config.schemasForModel()) {
            if (schema == null || schema.getName() == null || schema.getName().trim().isEmpty()) {
                continue;
            }
            String name = schema.getName().trim();
            config.handlerOf(name).ifPresent(h -> handlers.put(name, h));
        }
        return handlers;
    }
}

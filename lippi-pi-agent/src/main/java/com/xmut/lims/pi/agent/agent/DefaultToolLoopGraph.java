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

/**
 * 默认 Tool-loop 图：{@code START → agent ⇄ tools → agent → END}。
 *
 * <p>WRITE HITL：{@code tools} 节点经 bus {@code before_tool_call} 申请挂起；resume 重跑同一节点。
 * Policy 由 Session 在 bus 上 {@code register}，不进图、不进 ToolNode 构造。
 * Memory 由 {@code AgentSession.prompt} 预加载，图内不再含 {@code load_memory}。
 */
public final class DefaultToolLoopGraph {

    public static final String NODE_AGENT = ToolLoopGraphFactory.NODE_AGENT;
    public static final String NODE_TOOLS = ToolLoopGraphFactory.NODE_TOOLS;

    private DefaultToolLoopGraph() {
    }

    public static StateGraph build() {
        return ToolLoopGraphFactory.createDefault();
    }

    public static StateGraph build(ModelProvider modelProvider) {
        return ToolLoopGraphFactory.createDefault(modelProvider);
    }

    public static StateGraph build(ModelProvider modelProvider, PromptBuilder promptBuilder) {
        return ToolLoopGraphFactory.createDefault(modelProvider, promptBuilder);
    }

    public static StateGraph build(ModelProvider modelProvider,
                                   PromptBuilder promptBuilder,
                                   ToolConfig toolConfig) {
        return ToolLoopGraphFactory.createDefault(modelProvider, promptBuilder, toolConfig);
    }

    public static StateGraph build(ModelProvider modelProvider,
                                   PromptBuilder promptBuilder,
                                   ToolConfig toolConfig,
                                   ContextCompressor compressor) {
        return ToolLoopGraphFactory.createDefault(modelProvider, promptBuilder, toolConfig, compressor);
    }

    public static StateGraph build(AgentTurnNode agentNode, ToolConfig toolConfig) {
        return ToolLoopGraphFactory.create(agentNode, toolConfig);
    }

    public static StateGraph create(GraphNode agentNode, ToolConfig toolConfig) {
        return ToolLoopGraphFactory.create(agentNode, toolConfig);
    }

    public static EdgeCondition hasToolCalls() {
        return ToolLoopGraphFactory.hasToolCalls();
    }

    /**
     * 拓扑装配；外部请走 {@link DefaultToolLoopGraph#build} / {@link DefaultToolLoopGraph#create}。
     */
    public static final class ToolLoopGraphFactory {

        public static final String NODE_AGENT = "agent";
        public static final String NODE_TOOLS = "tools";

        private ToolLoopGraphFactory() {
        }

        static StateGraph createDefault() {
            return create(AgentTurnNode.forTopologyTest(), DefaultToolConfig.empty());
        }

        static StateGraph createDefault(ModelProvider modelProvider) {
            return create(new AgentTurnNode(modelProvider), DefaultToolConfig.empty());
        }

        static StateGraph createDefault(ModelProvider modelProvider, PromptBuilder promptBuilder) {
            return create(new AgentTurnNode(modelProvider, promptBuilder), DefaultToolConfig.empty());
        }

        static StateGraph createDefault(ModelProvider modelProvider,
                                        PromptBuilder promptBuilder,
                                        ToolConfig toolConfig) {
            return createDefault(modelProvider, promptBuilder, toolConfig, ContextCompressor.NOOP);
        }

        static StateGraph createDefault(ModelProvider modelProvider,
                                        PromptBuilder promptBuilder,
                                        ToolConfig toolConfig,
                                        ContextCompressor compressor) {
            return create(
                    new AgentTurnNode(modelProvider, promptBuilder, compressor),
                    toolConfig);
        }

        static StateGraph create(GraphNode agentNode, ToolConfig toolConfig) {
            ToolNode toolNode = new ToolNode(handler(toolConfig));

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

        static Map<String, ToolHandler> handler(ToolConfig config) {
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
    }
}

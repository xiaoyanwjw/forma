package com.xmut.lims.pi.agent.graph;

import java.util.Map;

/**
 * 图节点：返回部分 state 更新（key-value）。
 */
@FunctionalInterface
public interface GraphNode {

    Map<String, Object> execute(GraphState state, NodeContext ctx);
}

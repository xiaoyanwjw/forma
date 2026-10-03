package com.xmut.lims.pi.agent.graph;

import java.util.Map;

/**
 * 图节点接口。
 * 功能描述：根据当前状态返回部分 state 更新。
 */
@FunctionalInterface
public interface GraphNode {

    Map<String, Object> execute(GraphState state, NodeContext ctx);
}

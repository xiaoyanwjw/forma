package com.xmut.lims.pi.agent.graph;

/**
 * 条件路由：读取状态返回 pathMap 键。
 */
@FunctionalInterface
public interface EdgeCondition {

    String route(GraphState state);
}

package com.xmut.forma.pi.agent.graph;

/**
 * 条件路由接口。
 * 功能描述：读取状态并返回 pathMap 键以选择下游边。
 */
@FunctionalInterface
public interface EdgeCondition {

    String route(GraphState state);
}

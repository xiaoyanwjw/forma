package com.xmut.lims.pi.agent.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 条件路由边：condition 返回值映射到目标节点名。
 */
final class ConditionalEdge {

    private final String from;
    private final EdgeCondition condition;
    private final Map<String, String> pathMap;

    ConditionalEdge(String from, EdgeCondition condition, Map<String, String> pathMap) {
        this.from = from;
        this.condition = condition;
        this.pathMap = Collections.unmodifiableMap(new HashMap<>(pathMap));
    }

    String getFrom() {
        return from;
    }

    Map<String, String> getPathMap() {
        return pathMap;
    }

    String resolve(GraphState state) {
        String routeKey = condition.route(state);
        String target = pathMap.get(routeKey);
        if (target == null) {
            throw new GraphExecutionException(
                    "Conditional edge from '" + from + "' returned route key '" + routeKey
                            + "' but no matching target in pathMap. Available keys: " + pathMap.keySet());
        }
        return target;
    }
}

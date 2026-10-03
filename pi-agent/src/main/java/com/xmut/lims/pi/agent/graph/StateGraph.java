package com.xmut.lims.pi.agent.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StateGraph 构建器。
 * 功能描述：声明节点与边并编译为 CompiledGraph。
 */
public final class StateGraph {

    public static final String START = "__start__";
    public static final String END = "__end__";

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<DirectEdge> directEdges = new ArrayList<>();
    private final List<ConditionalEdge> conditionalEdges = new ArrayList<>();
    private String entryPoint;

    public StateGraph addNode(String name, GraphNode node) {
        if (START.equals(name) || END.equals(name)) {
            throw new GraphCompilationException("Cannot use reserved name '" + name + "' as node name");
        }
        if (nodes.containsKey(name)) {
            throw new GraphCompilationException("Node '" + name + "' already registered");
        }
        nodes.put(name, node);
        return this;
    }

    public StateGraph addEdge(String from, String to) {
        directEdges.add(new DirectEdge(from, to));
        return this;
    }

    public StateGraph addConditionalEdges(String from, EdgeCondition condition, Map<String, String> pathMap) {
        conditionalEdges.add(new ConditionalEdge(from, condition, pathMap));
        return this;
    }

    public StateGraph setEntryPoint(String node) {
        this.entryPoint = node;
        return this;
    }

    public CompiledGraph compile() {
        return compile(CompileConfig.builder().build());
    }

    public CompiledGraph compile(CompileConfig config) {
        validate(config);

        String resolvedEntry = resolveEntryPoint();

        Map<String, DirectEdge> directRoutes = new HashMap<>();
        Map<String, ConditionalEdge> conditionalRoutes = new HashMap<>();

        for (DirectEdge edge : directEdges) {
            if (!START.equals(edge.getFrom())) {
                if (directRoutes.containsKey(edge.getFrom())) {
                    throw new GraphCompilationException(
                            "Duplicate direct edge from node '" + edge.getFrom() + "'");
                }
                directRoutes.put(edge.getFrom(), edge);
            }
        }
        for (ConditionalEdge edge : conditionalEdges) {
            if (START.equals(edge.getFrom())) {
                throw new GraphCompilationException(
                        "Conditional edge from START is not supported; use addEdge(START, ...) or setEntryPoint");
            }
            if (directRoutes.containsKey(edge.getFrom())) {
                throw new GraphCompilationException(
                        "Node '" + edge.getFrom() + "' has both direct and conditional edges");
            }
            if (conditionalRoutes.containsKey(edge.getFrom())) {
                throw new GraphCompilationException(
                        "Duplicate conditional edge from node '" + edge.getFrom() + "'");
            }
            conditionalRoutes.put(edge.getFrom(), edge);
        }

        return new CompiledGraph(
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
                directRoutes,
                conditionalRoutes,
                resolvedEntry,
                config
        );
    }

    private String resolveEntryPoint() {
        if (entryPoint != null) {
            return entryPoint;
        }
        for (DirectEdge edge : directEdges) {
            if (START.equals(edge.getFrom())) {
                return edge.getTo();
            }
        }
        throw new GraphCompilationException("Entry point not set and no direct edge from START found");
    }

    private void validate(CompileConfig config) {
        boolean hasStartEdge = false;
        for (DirectEdge edge : directEdges) {
            if (START.equals(edge.getFrom())) {
                hasStartEdge = true;
                break;
            }
        }
        if (entryPoint == null && !hasStartEdge) {
            throw new GraphCompilationException("Entry point must be set (via setEntryPoint or addEdge from START)");
        }

        if (entryPoint != null && !nodes.containsKey(entryPoint)) {
            throw new GraphCompilationException("Entry point '" + entryPoint + "' not registered as a node");
        }

        Set<String> validNames = new HashSet<>(nodes.keySet());
        validNames.add(START);
        validNames.add(END);

        for (DirectEdge edge : directEdges) {
            if (!validNames.contains(edge.getFrom())) {
                throw new GraphCompilationException("Edge references unknown node '" + edge.getFrom() + "'");
            }
            if (!validNames.contains(edge.getTo())) {
                throw new GraphCompilationException("Edge references unknown node '" + edge.getTo() + "'");
            }
        }
        for (ConditionalEdge edge : conditionalEdges) {
            if (START.equals(edge.getFrom())) {
                throw new GraphCompilationException(
                        "Conditional edge from START is not supported; use addEdge(START, ...) or setEntryPoint");
            }
            if (!validNames.contains(edge.getFrom())) {
                throw new GraphCompilationException("ConditionalEdge references unknown source node '" + edge.getFrom() + "'");
            }
            for (String target : edge.getPathMap().values()) {
                if (!validNames.contains(target)) {
                    throw new GraphCompilationException("ConditionalEdge pathMap references unknown target node '" + target + "'");
                }
            }
        }

        Set<String> reachable = new HashSet<>();
        String resolvedEntry = entryPoint;
        if (resolvedEntry == null) {
            for (DirectEdge edge : directEdges) {
                if (START.equals(edge.getFrom())) {
                    resolvedEntry = edge.getTo();
                    break;
                }
            }
        }
        if (resolvedEntry != null) {
            reachable.add(resolvedEntry);
        }
        for (DirectEdge edge : directEdges) {
            if (!END.equals(edge.getTo()) && !START.equals(edge.getTo())) {
                reachable.add(edge.getTo());
            }
        }
        for (ConditionalEdge edge : conditionalEdges) {
            for (String target : edge.getPathMap().values()) {
                if (!END.equals(target) && !START.equals(target)) {
                    reachable.add(target);
                }
            }
        }
        for (String nodeName : nodes.keySet()) {
            if (!reachable.contains(nodeName)) {
                throw new GraphCompilationException("Node '" + nodeName + "' is orphaned (no incoming edge and not entry point)");
            }
        }

        for (String name : config.getInterruptBefore()) {
            if (!nodes.containsKey(name)) {
                throw new GraphCompilationException("interruptBefore references unknown node '" + name + "'");
            }
        }
        for (String name : config.getInterruptAfter()) {
            if (!nodes.containsKey(name)) {
                throw new GraphCompilationException("interruptAfter references unknown node '" + name + "'");
            }
            if (config.getInterruptBefore().contains(name)) {
                throw new GraphCompilationException(
                        "Node '" + name + "' cannot be in both interruptBefore and interruptAfter");
            }
        }
    }
}
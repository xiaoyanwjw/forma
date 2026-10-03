package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.ai.model.ToolSchema;

import java.util.Objects;

/**
 * 工具运行时绑定。
 * 功能描述：组合 ToolDefinition（声明）与 ToolHandler（执行）。
 */
public final class ToolBinding {

    private final ToolDefinition definition;
    private final ToolHandler handler;

    public ToolBinding(ToolDefinition definition, ToolHandler handler) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.handler = handler;
    }

    public static ToolBinding of(ToolDefinition definition, ToolHandler handler) {
        return new ToolBinding(definition, handler);
    }

    /**
     * 便捷构造（测试 / 旧调用方）：等价于最小 Definition + handler。
     */
    public static ToolBinding of(String id, ToolHandler handler) {
        return of(ToolDefinition.builder().id(id).build(), handler);
    }

    public static ToolBinding of(String id, ToolSchema schema, ToolHandler handler) {
        return of(ToolDefinition.builder().id(id).schema(schema).build(), handler);
    }

    /**
     * 仅贡献 Handler：占位 Definition 会被同 id 的完整定义覆盖。
     */
    public static ToolBinding handlerOnly(String id, ToolHandler handler) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("tool id required");
        }
        Objects.requireNonNull(handler, "handler");
        ToolDefinition stub = ToolDefinition.builder()
                .id(id.trim())
                .build();
        return of(stub, handler);
    }

    public ToolDefinition getDefinition() {
        return definition;
    }

    public ToolHandler getHandler() {
        return handler;
    }

    public String getId() {
        return definition.getId();
    }
}

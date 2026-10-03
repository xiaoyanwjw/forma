package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.ai.model.ToolSchema;

/**
 * 已安装工具（定义 + 执行）。
 * 功能描述：等于 {@link ToolDefinition} + {@link ToolHandler}，供启动装入 {@link ToolCatalog}。
 */
public final class Tool {

    private final ToolBinding binding;

    public Tool(ToolDefinition definition, ToolHandler handler) {
        this.binding = ToolBinding.of(definition, handler);
    }

    /**
     * 兼容构造：隐式最小 {@link ToolDefinition}。
     */
    public Tool(String name, ToolSchema schema, ToolHandler handler) {
        this(ToolDefinition.builder()
                        .id(name)
                        .schema(schema)
                        .build(),
                handler);
    }

    public static Tool of(ToolBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("binding required");
        }
        return new Tool(binding.getDefinition(), binding.getHandler());
    }

    public ToolDefinition getDefinition() {
        return binding.getDefinition();
    }

    public ToolBinding getBinding() {
        return binding;
    }

    public String getName() {
        return binding.getId();
    }

    public ToolSchema getSchema() {
        return binding.getDefinition().getSchema();
    }

    public ToolHandler getHandler() {
        return binding.getHandler();
    }
}

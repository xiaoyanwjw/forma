package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试辅助：按名注册 handler。
 */
public final class ToolTestSupport {

    private ToolTestSupport() {}

    public static InMemoryToolCatalog config(Map<String, ToolHandler> handlers) {
        List<Tool> tools = new ArrayList<>();
        if (handlers != null) {
            for (Map.Entry<String, ToolHandler> e : handlers.entrySet()) {
                tools.add(tool(e.getKey(), e.getValue()));
            }
        }
        return new InMemoryToolCatalog(tools);
    }

    public static InMemoryToolCatalog readConfig(Map<String, ToolHandler> handlers) {
        return config(handlers);
    }

    public static Tool tool(String name, ToolHandler handler) {
        return new Tool(
                ToolDefinition.builder()
                        .id(name)
                        .schema(ToolSchema.builder().name(name).build())
                        .build(),
                handler);
    }

    public static Tool tool(String name,
                            String text,
                            ToolHandler handler) {
        return new Tool(
                ToolDefinition.builder()
                        .id(name)
                        .schema(ToolSchema.builder().name(name).build())
                        .text(text)
                        .build(),
                handler);
    }
}

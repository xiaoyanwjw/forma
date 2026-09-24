package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试辅助：把 handler map 注册为指定级别。
 */
public final class ToolTestSupport {

    private ToolTestSupport() {}

    public static DefaultToolConfig config(Map<String, ToolHandler> handlers, ToolLevel level) {
        List<ToolRegistration> regs = new ArrayList<>();
        if (handlers != null) {
            for (Map.Entry<String, ToolHandler> e : handlers.entrySet()) {
                regs.add(registration(e.getKey(), level, e.getValue()));
            }
        }
        return new DefaultToolConfig(regs);
    }

    public static DefaultToolConfig readConfig(Map<String, ToolHandler> handlers) {
        return config(handlers, ToolLevel.READ);
    }

    public static ToolRegistration registration(String name, ToolLevel level, ToolHandler handler) {
        return new ToolRegistration(
                ToolManifest.builder()
                        .id(name)
                        .schema(ToolSchema.builder().name(name).build())
                        .level(level)
                        .build(),
                handler);
    }

    public static ToolRegistration registration(String name,
                                                ToolLevel level,
                                                String text,
                                                ToolHandler handler) {
        return new ToolRegistration(
                ToolManifest.builder()
                        .id(name)
                        .schema(ToolSchema.builder().name(name).build())
                        .level(level)
                        .text(text)
                        .build(),
                handler);
    }
}
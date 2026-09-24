package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;

/**
 * 已安装工具条目 = {@link ToolManifest} + {@link ToolHandler}。
 *
 * <p>保留此类型以兼容既有装配/测试；新代码优先用 {@link ToolManifest} + {@link ToolBinding}。
 * {@code getName()/getSchema()/getLevel()} 委托 Manifest。
 */
public final class ToolRegistration {

    private final ToolBinding binding;

    public ToolRegistration(ToolManifest manifest, ToolHandler handler) {
        this.binding = ToolBinding.of(manifest, handler);
    }

    /**
     * 兼容构造：隐式最小 {@link ToolManifest}。
     */
    public ToolRegistration(String name, ToolSchema schema, ToolLevel level, ToolHandler handler) {
        this(ToolManifest.builder()
                        .id(name)
                        .schema(schema)
                        .level(level)
                        .build(),
                handler);
    }

    public static ToolRegistration of(ToolBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("binding required");
        }
        return new ToolRegistration(binding.getManifest(), binding.getHandler());
    }

    public ToolManifest getManifest() {
        return binding.getManifest();
    }

    public ToolBinding getBinding() {
        return binding;
    }

    public String getName() {
        return binding.getId();
    }

    public ToolSchema getSchema() {
        return binding.getManifest().getSchema();
    }

    public ToolLevel getLevel() {
        return binding.getLevel();
    }

    public ToolHandler getHandler() {
        return binding.getHandler();
    }
}

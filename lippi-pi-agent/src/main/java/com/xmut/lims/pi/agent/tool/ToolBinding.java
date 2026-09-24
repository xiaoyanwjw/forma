package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;

import java.util.Objects;

/**
 * 工具运行时绑定：{@link ToolManifest}（声明）+ {@link ToolHandler}（执行）。
 *
 * <p>与 Skill 侧对比：Skill 只有 Manifest（无 handler）；Tool 需要 Binding 才能跑。
 */
public final class ToolBinding {

    private final ToolManifest manifest;
    private final ToolHandler handler;

    public ToolBinding(ToolManifest manifest, ToolHandler handler) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.handler = handler;
    }

    public static ToolBinding of(ToolManifest manifest, ToolHandler handler) {
        return new ToolBinding(manifest, handler);
    }

    /**
     * 便捷构造（测试 / 旧调用方）：等价于最小 Manifest + handler。
     */
    public static ToolBinding of(String id, ToolLevel level, ToolHandler handler) {
        return of(ToolManifest.builder().id(id).level(level).build(), handler);
    }

    public static ToolBinding of(String id, ToolSchema schema, ToolLevel level, ToolHandler handler) {
        return of(ToolManifest.builder().id(id).schema(schema).level(level).build(), handler);
    }

    /**
     * 仅贡献 Handler：占位 Manifest 会被 classpath JSON 同 id 覆盖。
     *
     * <p>生产路径由 {@link ToolHandlerAutoBinder} 按 JSON {@code handlerClass} 生成。
     */
    public static ToolBinding handlerOnly(String id, ToolHandler handler) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("tool id required");
        }
        Objects.requireNonNull(handler, "handler");
        ToolManifest stub = ToolManifest.builder()
                .id(id.trim())
                .level(ToolLevel.READ)
                .build();
        return of(stub, handler);
    }

    public ToolManifest getManifest() {
        return manifest;
    }

    public ToolHandler getHandler() {
        return handler;
    }

    public String getId() {
        return manifest.getId();
    }

    public ToolLevel getLevel() {
        return manifest.getLevel();
    }
}

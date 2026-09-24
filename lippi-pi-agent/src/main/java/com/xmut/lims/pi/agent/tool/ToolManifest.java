package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.ai.model.ToolSchema;
import lombok.Builder;
import lombok.Value;

/**
 * 不可变工具资产说明书（与 {@code SkillManifest} 对称）。
 *
 * <p>声明「是什么 / 什么级别 / 给模型看什么」；<strong>不含</strong> {@link com.xmut.lims.pi.agent.graph.node.ToolHandler}。
 * 执行绑定见 {@link ToolBinding} / {@link ToolRegistration}。
 *
 * <p>{@link #text} → Stable {@code tools} 段；{@link #schema} → API {@code AVAILABLE_TOOLS}。
 */
@Value
@Builder(toBuilder = true)
public class ToolManifest {

    /** 稳定工具键（与 SkillManifest.id 同形）。 */
    String id;

    /** 可选版本；推荐 semver。 */
    String version;

    String displayName;

    String description;

    /**
     * → system stable：tools 文本（与 SkillManifest.skillsPrompt → skills 文本对称）。
     */
    String text;

    /** → API tools schema；可空则按 id 生成最小 schema。 */
    ToolSchema schema;

    /** 分级；FORBIDDEN 可入册但不暴露给模型。 */
    ToolLevel level;

    /**
     * 可选：Handler 全限定类名（如 {@code com.xmut.lims.pi.agent.tool.handler.ReadSkillHandler}）。
     * 由 {@link ToolHandlerAutoBinder} 解析；不进模型 schema。
     */
    String handlerClass;

    ToolManifest(String id,
                 String version,
                 String displayName,
                 String description,
                 String text,
                 ToolSchema schema,
                 ToolLevel level,
                 String handlerClass) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("tool manifest id required");
        }
        if (level == null) {
            throw new IllegalArgumentException("tool manifest level required");
        }
        this.id = id.trim();
        this.version = version;
        this.displayName = displayName;
        this.description = description;
        this.text = text;
        this.schema = schema;
        this.level = level;
        this.handlerClass = handlerClass;
    }

    /** 暴露给模型的 schema（缺省用 id 作 name）。 */
    public ToolSchema schemaOrDefault() {
        if (schema != null) {
            return schema;
        }
        return ToolSchema.builder().name(id).description(description).build();
    }
}

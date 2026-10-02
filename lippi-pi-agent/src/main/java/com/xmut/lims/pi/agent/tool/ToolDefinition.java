package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.ai.model.ToolSchema;
import lombok.Builder;
import lombok.Value;

/**
 * 不可变工具定义（对齐官方 Pi {@code ToolDefinition}）。
 * 功能描述：声明工具名称、给模型看的文本/schema；执行见 {@link ToolBinding}/{@link ToolHandler}。
 * 关键设计：启用按名字白名单，不分级。
 */
@Value
@Builder(toBuilder = true)
public class ToolDefinition {

    /** 稳定工具键（与 Skill.id 同形）。 */
    String id;

    /** 可选版本。 */
    String version;

    String displayName;

    String description;

    /**
     * → system stable：tools 文本（与 Skill catalog 文本对称）。
     */
    String text;

    /** → API tools schema；可空则按 id 生成最小 schema。 */
    ToolSchema schema;

    /**
     * 可选：Handler 全限定类名（如 {@code com.xmut.lims.pi.agent.tool.base.ReadSkill}）。
     * 由 {@link ToolHandlerAutoBinder} 解析；不进模型 schema。
     */
    String handlerClass;

    ToolDefinition(String id,
                 String version,
                 String displayName,
                 String description,
                 String text,
                 ToolSchema schema,
                 String handlerClass) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("tool definition id required");
        }
        this.id = id.trim();
        this.version = version;
        this.displayName = displayName;
        this.description = description;
        this.text = text;
        this.schema = schema;
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

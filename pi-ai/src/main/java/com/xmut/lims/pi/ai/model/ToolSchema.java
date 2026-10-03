package com.xmut.lims.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * 工具 schema（传给模型的 function 描述）。
 *
 * <p>hermes 自有类型；不依赖 agent Tool / Capability。
 */
@Value
@Builder
public class ToolSchema {

    /** 工具名（模型侧 function name）。 */
    String name;

    /** 人类可读说明（可空）。 */
    String description;

    /**
     * 参数 JSON Schema（可空）。
     *
     * <p>{@code arguments} 在响应侧须为单次 JSON 解析的 {@link JsonNode}，禁止 double-encode。
     */
    JsonNode parametersSchema;
}

package com.xmut.lims.pi.ai.model;

import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * 模型补全响应 <b>[Lippi]</b>。
 *
 * <p><strong>禁止</strong>使用 agent 风格 {@code ChatResponse} 作 hermes 公共 API。
 * {@code toolCalls} 永不 null（无工具调用时为空列表）。
 */
@Value
@Builder
public class ModelResponse {

    /** 助手文本内容（可空）。 */
    String content;

    /** 结构化工具调用；永不 null。 */
    @Builder.Default
    List<ToolCallEntry> toolCalls = Collections.emptyList();

    /** 可选：stop / tool_calls / length 等。 */
    String finishReason;

    /** 可选：实际模型版本。 */
    String modelVersion;

    Integer promptTokens;

    Integer completionTokens;

    Integer totalTokens;

    public List<ToolCallEntry> getToolCalls() {
        return toolCalls != null ? toolCalls : Collections.emptyList();
    }

    /**
     * 规范化：保证 {@code toolCalls} 非 null。
     */
    public static ModelResponse norm(ModelResponse response) {
        if (response == null) {
            return ModelResponse.builder().toolCalls(Collections.emptyList()).build();
        }
        if (response.toolCalls != null) {
            return response;
        }
        return ModelResponse.builder()
                .content(response.content)
                .toolCalls(Collections.emptyList())
                .finishReason(response.finishReason)
                .modelVersion(response.modelVersion)
                .promptTokens(response.promptTokens)
                .completionTokens(response.completionTokens)
                .totalTokens(response.totalTokens)
                .build();
    }
}

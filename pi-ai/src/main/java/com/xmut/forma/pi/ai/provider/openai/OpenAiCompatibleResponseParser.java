package com.xmut.forma.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.exception.PiModelIoException;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.model.NativeToolCallMapper;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;

import java.util.Collections;
import java.util.List;

/**
 * OpenAI 兼容响应 → {@link ModelResponse}。
 */
public class OpenAiCompatibleResponseParser {

    private final ObjectMapper mapper;
    private final NativeToolCallMapper toolCallMapper;

    public OpenAiCompatibleResponseParser(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolCallMapper = new NativeToolCallMapper(this.mapper);
    }

    public ModelResponse parse(String raw, String fallbackModel) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new PiModelIoException("empty response body");
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            throw new PiModelIoException("response not JSON", e);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new PiModelIoException("response missing choices");
        }
        JsonNode choice0 = choices.get(0);
        String finishReason = choice0.path("finish_reason").asText(null);
        JsonNode messageNode = choice0.path("message");
        String content = messageNode.path("content").asText("");
        List<ToolCallEntry> toolCalls = toolCallMapper.fromJsonArray(messageNode.path("tool_calls"));
        if (toolCalls == null) {
            toolCalls = Collections.emptyList();
        }
        String modelVersion = root.path("model").asText(fallbackModel);
        JsonNode usage = root.path("usage");
        Integer total = null;
        Integer prompt = null;
        Integer completion = null;
        if (usage != null && usage.isObject() && !usage.isMissingNode()) {
            if (usage.has("total_tokens")) {
                total = usage.path("total_tokens").asInt();
            }
            if (usage.has("prompt_tokens")) {
                prompt = usage.path("prompt_tokens").asInt();
            }
            if (usage.has("completion_tokens")) {
                completion = usage.path("completion_tokens").asInt();
            }
        }
        return ModelResponse.builder()
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .modelVersion(modelVersion)
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .build();
    }
}

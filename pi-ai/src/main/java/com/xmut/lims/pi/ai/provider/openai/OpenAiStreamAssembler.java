package com.xmut.lims.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.NativeToolCallMapper;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 把 OpenAI 兼容 SSE chunk 拼成文本 delta 与最终 {@link ModelResponse}。
 */
final class OpenAiStreamAssembler {

    private final ObjectMapper mapper;
    private final NativeToolCallMapper toolCallMapper;
    private final String fallbackModel;
    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, ToolAcc> tools = new TreeMap<Integer, ToolAcc>();
    private String finishReason;
    private String modelVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    OpenAiStreamAssembler(ObjectMapper mapper, String fallbackModel) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.toolCallMapper = new NativeToolCallMapper(this.mapper);
        this.fallbackModel = fallbackModel;
    }

    /**
     * @return 本 chunk 的文本增量；无文本则 null
     */
    String accept(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull() && !error.isMissingNode()) {
            String msg = error.path("message").asText(error.toString());
            throw new com.xmut.lims.pi.ai.exception.PiModelIoException("stream error: " + msg);
        }
        if (root.has("model") && root.path("model").isTextual()) {
            String m = root.path("model").asText();
            if (m != null && !m.isEmpty()) {
                modelVersion = m;
            }
        }
        JsonNode usage = root.get("usage");
        if (usage != null && usage.isObject()) {
            if (usage.has("total_tokens")) {
                totalTokens = usage.path("total_tokens").asInt();
            }
            if (usage.has("prompt_tokens")) {
                promptTokens = usage.path("prompt_tokens").asInt();
            }
            if (usage.has("completion_tokens")) {
                completionTokens = usage.path("completion_tokens").asInt();
            }
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            return null;
        }
        JsonNode choice0 = choices.get(0);
        String fr = choice0.path("finish_reason").asText(null);
        if (fr != null && !fr.isEmpty() && !"null".equals(fr)) {
            finishReason = fr;
        }
        JsonNode delta = choice0.path("delta");
        absorbToolCalls(delta.path("tool_calls"));
        if (!delta.has("content") || !delta.path("content").isTextual()) {
            return null;
        }
        String piece = delta.path("content").asText();
        if (piece == null || piece.isEmpty()) {
            return null;
        }
        content.append(piece);
        return piece;
    }

    ModelResponse build() {
        List<ToolCallEntry> calls = new ArrayList<ToolCallEntry>();
        for (ToolAcc acc : tools.values()) {
            if (acc.name == null || acc.name.isEmpty()) {
                continue;
            }
            com.fasterxml.jackson.databind.node.ObjectNode item = mapper.createObjectNode();
            if (acc.id != null) {
                item.put("id", acc.id);
            }
            com.fasterxml.jackson.databind.node.ObjectNode fn = item.putObject("function");
            fn.put("name", acc.name);
            fn.put("arguments", acc.arguments.length() == 0 ? "{}" : acc.arguments.toString());
            ToolCallEntry entry = toolCallMapper.fromJson(item);
            if (entry != null) {
                calls.add(entry);
            }
        }
        String reason = finishReason;
        if (reason == null || reason.isEmpty()) {
            reason = calls.isEmpty() ? "stop" : "tool_calls";
        }
        return ModelResponse.builder()
                .content(content.toString())
                .toolCalls(calls)
                .finishReason(reason)
                .modelVersion(modelVersion != null ? modelVersion : fallbackModel)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .build();
    }

    private void absorbToolCalls(JsonNode tcs) {
        if (tcs == null || !tcs.isArray()) {
            return;
        }
        for (JsonNode tc : tcs) {
            if (tc == null || !tc.isObject()) {
                continue;
            }
            int index = tc.path("index").asInt(0);
            ToolAcc acc = tools.get(index);
            if (acc == null) {
                acc = new ToolAcc();
                tools.put(index, acc);
            }
            if (tc.has("id") && tc.path("id").isTextual()) {
                String id = tc.path("id").asText();
                if (id != null && !id.isEmpty()) {
                    acc.id = id;
                }
            }
            JsonNode fn = tc.path("function");
            if (fn != null && fn.isObject()) {
                if (fn.has("name") && fn.path("name").isTextual()) {
                    String name = fn.path("name").asText();
                    if (name != null && !name.isEmpty()) {
                        acc.name = name;
                    }
                }
                if (fn.has("arguments") && fn.path("arguments").isTextual()) {
                    String piece = fn.path("arguments").asText();
                    if (piece != null) {
                        acc.arguments.append(piece);
                    }
                }
            }
        }
    }

    private static final class ToolAcc {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}

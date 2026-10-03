package com.xmut.forma.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Native {@code tool_calls[]} → {@link ToolCallEntry} 映射（ADR-C-08）。
 *
 * <p>{@code arguments} 保证为 {@link JsonNode} 对象/数组，禁止以 JSON 字符串二次编码。
 */
public final class NativeToolCallMapper {

    private final ObjectMapper mapper;

    public NativeToolCallMapper() {
        this(new ObjectMapper());
    }

    public NativeToolCallMapper(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    /**
     * 规范化已有 {@link ToolCallEntry}：对 string-typed arguments 解一层。
     */
    public List<ToolCallEntry> normalize(List<ToolCallEntry> calls) {
        if (calls == null || calls.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolCallEntry> out = new ArrayList<>(calls.size());
        for (ToolCallEntry call : calls) {
            if (call == null) {
                continue;
            }
            String name = call.getToolName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            out.add(new ToolCallEntry(
                    call.getId() != null ? call.getId() : generateId(),
                    name,
                    decodeArguments(call.getArguments())));
        }
        return out;
    }

    /**
     * 从原始 JSON 节点列表解析（适配器可用）。
     *
     * <p>期望元素形如 {@code {"id","function":{"name","arguments"}}} 或扁平
     * {@code {"id","name","arguments"}}。
     */
    public List<ToolCallEntry> fromJsonArray(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return Collections.emptyList();
        }
        List<ToolCallEntry> out = new ArrayList<>(array.size());
        for (JsonNode item : array) {
            ToolCallEntry entry = fromJson(item);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    public ToolCallEntry fromJson(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        String id = textOr(item.get("id"), generateId());
        String name;
        JsonNode argsNode;
        JsonNode function = item.get("function");
        if (function != null && function.isObject()) {
            name = textOr(function.get("name"), null);
            argsNode = function.get("arguments");
        } else {
            name = textOr(item.get("name"), textOr(item.get("toolName"), null));
            argsNode = item.get("arguments");
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        return new ToolCallEntry(id, name, decodeArguments(argsNode));
    }

    JsonNode decodeArguments(JsonNode argsNode) {
        if (argsNode == null || argsNode.isNull()) {
            return mapper.createObjectNode();
        }
        if (argsNode.isTextual()) {
            String raw = argsNode.asText();
            if (raw == null || raw.isEmpty()) {
                return mapper.createObjectNode();
            }
            try {
                JsonNode parsed = mapper.readTree(raw);
                // 禁 double-encode：若仍是字符串且像 JSON，再解一层后返回对象
                if (parsed != null && parsed.isTextual()) {
                    String inner = parsed.asText();
                    if (inner != null && (inner.startsWith("{") || inner.startsWith("["))) {
                        try {
                            return mapper.readTree(inner);
                        } catch (Exception ignore) {
                            return mapper.createObjectNode();
                        }
                    }
                }
                return parsed != null ? parsed : mapper.createObjectNode();
            } catch (Exception e) {
                return mapper.createObjectNode();
            }
        }
        return argsNode;
    }

    private static String textOr(JsonNode node, String fallback) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return fallback;
        }
        String v = node.asText();
        return v != null && !v.isEmpty() ? v : fallback;
    }

    private static String generateId() {
        return "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

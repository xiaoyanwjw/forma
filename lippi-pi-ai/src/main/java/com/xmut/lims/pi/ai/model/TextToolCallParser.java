package com.xmut.lims.pi.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.message.Message;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-C-08 文本协议回落解析器：从助手文本提取 {@code Action} / {@code Action Input}。
 *
 * <p>{@code arguments} 经 {@link ObjectMapper} <strong>单次</strong>解析为 {@link JsonNode}，
 * 禁止把 JSON 字符串再包一层字符串（double-encode）。
 */
public final class TextToolCallParser {

    private static final Pattern ACTION = Pattern.compile(
            "(?im)^\\s*Action\\s*:\\s*(.+?)\\s*$");
    private static final Pattern ACTION_INPUT = Pattern.compile(
            "(?is)\\A\\s*Action\\s+Input\\s*:\\s*(.*)\\s*\\z");

    private final ObjectMapper mapper;

    public TextToolCallParser() {
        this(new ObjectMapper());
    }

    public TextToolCallParser(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    /**
     * 解析文本；无匹配时返回空列表（非 null）。支持同一回复中多个 Action 块。
     */
    public List<ToolCallEntry> parse(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }

        List<ToolCallEntry> out = new ArrayList<>();
        Matcher actionMatcher = ACTION.matcher(content);
        List<int[]> actionSpans = new ArrayList<>();
        List<String> actionNames = new ArrayList<>();
        while (actionMatcher.find()) {
            actionSpans.add(new int[]{actionMatcher.start(), actionMatcher.end()});
            actionNames.add(actionMatcher.group(1).trim());
        }
        for (int i = 0; i < actionSpans.size(); i++) {
            String toolName = actionNames.get(i);
            if (toolName.isEmpty() || "none".equalsIgnoreCase(toolName)
                    || "finish".equalsIgnoreCase(toolName)) {
                continue;
            }
            int regionStart = actionSpans.get(i)[1];
            int regionEnd = (i + 1 < actionSpans.size()) ? actionSpans.get(i + 1)[0] : content.length();
            String region = content.substring(regionStart, regionEnd);
            JsonNode arguments = emptyObject();
            Matcher inputMatcher = ACTION_INPUT.matcher(region);
            if (inputMatcher.find()) {
                arguments = parseArgumentsOnce(inputMatcher.group(1).trim());
            }
            String id = "text-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            out.add(new ToolCallEntry(id, toolName, arguments));
        }
        return out;
    }

    /**
     * 去掉 Action / Action Input 脚手架，保留 Thought 等自然语言（若有）。
     */
    public static String stripToolScaffolding(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String stripped = content
                .replaceAll("(?im)^\\s*Action\\s*:\\s*.*$\\n?", "")
                .replaceAll("(?is)\\s*Action\\s+Input\\s*:\\s*.*", "")
                .trim();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 将工具 schema 注入为 system 提示（文本回落路径用）。
     *
     * <p>若已有 system 消息，则把工具说明<strong>追加</strong>到首条 system，避免重复 system 冲突。
     */
    public static List<Message> withToolPrompt(List<Message> messages, List<ToolSchema> tools) {
        String prompt = buildToolPrompt(tools);
        List<Message> out = new ArrayList<>();
        boolean merged = false;
        if (messages != null) {
            for (Message m : messages) {
                if (m == null) {
                    continue;
                }
                if (!merged && "system".equals(m.getRole())) {
                    String existing = m.getContent() != null ? m.getContent() : "";
                    out.add(m.toBuilder()
                            .content(existing.isEmpty() ? prompt : existing + "\n\n" + prompt)
                            .build());
                    merged = true;
                } else {
                    out.add(m);
                }
            }
        }
        if (!merged) {
            out.add(0, Message.builder().role("system").content(prompt).build());
        }
        return out;
    }

    static String buildToolPrompt(List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("You may call tools using this exact format:\n");
        sb.append("Action: <tool_name>\n");
        sb.append("Action Input: <json_object>\n");
        sb.append("Or reply without tools when finished.\n");
        sb.append("Available tools:\n");
        if (tools != null) {
            for (ToolSchema t : tools) {
                if (t == null || t.getName() == null || t.getName().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(t.getName());
                if (t.getDescription() != null) {
                    sb.append(": ").append(t.getDescription());
                }
                if (t.getParametersSchema() != null) {
                    sb.append(" schema=").append(t.getParametersSchema());
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    JsonNode parseArgumentsOnce(String raw) {
        if (raw == null || raw.isEmpty()) {
            return emptyObject();
        }
        try {
            JsonNode node = mapper.readTree(raw);
            // 若误 double-encode 成 JSON 字符串，再解一层；正常对象/数组直接返回
            if (node != null && node.isTextual()) {
                String inner = node.asText();
                if (inner != null && (inner.startsWith("{") || inner.startsWith("["))) {
                    try {
                        return mapper.readTree(inner);
                    } catch (Exception ignore) {
                        ObjectNode fallback = mapper.createObjectNode();
                        fallback.put("raw", raw);
                        return fallback;
                    }
                }
            }
            return node != null ? node : emptyObject();
        } catch (Exception e) {
            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("raw", raw);
            return fallback;
        }
    }

    private JsonNode emptyObject() {
        return mapper.createObjectNode();
    }
}

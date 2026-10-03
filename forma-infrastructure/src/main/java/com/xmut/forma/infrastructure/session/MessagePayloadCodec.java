package com.xmut.forma.infrastructure.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.pi.ai.message.ContentPart;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Message 与 entry.payload 编解码器。
 * 功能描述：在单个 Message JSON 对象与领域 Message 之间转换。
 * 关键设计：payload 非数组、非 envelope（1 行 = 1 Message）。
 */
final class MessagePayloadCodec {

    private MessagePayloadCodec() {
    }

    static String toPayload(ObjectMapper mapper, Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message required");
        }
        if (!StringUtils.hasText(message.getRole())) {
            throw new IllegalArgumentException("message.role required");
        }
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", message.getRole());
            if (message.getContent() != null) {
                node.put("content", message.getContent());
            } else {
                node.putNull("content");
            }
            if (message.hasParts()) {
                node.set("parts", mapper.valueToTree(message.getParts()));
            }
            if (StringUtils.hasText(message.getToolCallId())) {
                node.put("toolCallId", message.getToolCallId());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                ArrayNode arr = node.putArray("toolCalls");
                for (ToolCallEntry tc : message.getToolCalls()) {
                    if (tc == null) {
                        continue;
                    }
                    ObjectNode t = arr.addObject();
                    if (tc.getId() != null) {
                        t.put("id", tc.getId());
                    }
                    if (tc.getToolName() != null) {
                        t.put("toolName", tc.getToolName());
                    }
                    if (tc.getArguments() != null) {
                        t.set("arguments", tc.getArguments());
                    }
                }
            }
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("message payload serialize failed", e);
        }
    }

    static Message fromPayload(ObjectMapper mapper, String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("entry payload required");
        }
        try {
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("payload must be a Message JSON object");
            }
            Message.MessageBuilder builder = Message.builder()
                    .role(textOrNull(root, "role"))
                    .content(textOrNull(root, "content"));
            if (root.has("toolCallId") && !root.get("toolCallId").isNull()) {
                builder.toolCallId(root.get("toolCallId").asText());
            }
            if (root.has("parts") && root.get("parts").isArray()) {
                List<ContentPart> parts = new ArrayList<>();
                for (JsonNode p : root.get("parts")) {
                    if (p == null || p.isNull()) {
                        continue;
                    }
                    parts.add(ContentPart.builder()
                            .type(textOrNull(p, "type"))
                            .text(textOrNull(p, "text"))
                            .url(textOrNull(p, "url"))
                            .detail(textOrNull(p, "detail"))
                            .build());
                }
                builder.parts(parts);
            }
            if (root.has("toolCalls") && root.get("toolCalls").isArray()) {
                List<ToolCallEntry> calls = new ArrayList<>();
                for (JsonNode t : root.get("toolCalls")) {
                    if (t == null || t.isNull()) {
                        continue;
                    }
                    calls.add(new ToolCallEntry(
                            textOrNull(t, "id"),
                            textOrNull(t, "toolName"),
                            t.get("arguments")));
                }
                builder.toolCalls(calls);
            }
            return builder.build();
        } catch (IOException e) {
            throw new IllegalStateException("message payload deserialize failed", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}

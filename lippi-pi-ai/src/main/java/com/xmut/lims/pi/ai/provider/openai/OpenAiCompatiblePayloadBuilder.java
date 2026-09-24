package com.xmut.lims.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.ai.exception.PiModelIoException;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 把 {@link ModelRequest} 编成 OpenAI 兼容 JSON。有 image_url 时 content 用数组。
 */
public class OpenAiCompatiblePayloadBuilder {

    private final ObjectMapper mapper;

    public OpenAiCompatiblePayloadBuilder(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    public String build(ModelRequest request, ModelDescriptor desc, boolean stream) {
        ObjectNode root = mapper.createObjectNode();
        String model = desc != null && StringUtils.hasText(desc.getModel()) ? desc.getModel() : "qwen-flash";
        root.put("model", model);
        root.put("stream", stream);
        Double temperature = request != null && request.getTemperature() != null
                ? request.getTemperature()
                : (desc != null ? desc.getTemperature() : null);
        if (temperature != null) {
            root.put("temperature", temperature);
        }
        Integer maxTokens = request != null && request.getMaxTokens() != null
                ? request.getMaxTokens()
                : (desc != null ? desc.getMaxTokens() : null);
        if (maxTokens != null) {
            root.put("max_tokens", maxTokens);
        }

        // DeepSeek V4：显式开关 thinking，避免默认 high effort 吃光 max_tokens / 截断 JSON
        if (desc != null && StringUtils.hasText(desc.getThinkingMode())) {
            ObjectNode thinking = root.putObject("thinking");
            thinking.put("type", desc.getThinkingMode().trim());
        }

        ArrayNode messages = root.putArray("messages");
        List<Message> src = request != null ? request.getMessages() : null;
        if (src != null) {
            for (Message msg : src) {
                if (msg != null) {
                    messages.add(buildMessage(msg));
                }
            }
        }

        List<ToolSchema> tools = request != null ? request.getTools() : null;
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolSchema t : tools) {
                if (t == null || !StringUtils.hasText(t.getName())) {
                    continue;
                }
                ObjectNode tool = mapper.createObjectNode();
                tool.put("type", "function");
                ObjectNode fn = tool.putObject("function");
                fn.put("name", t.getName());
                if (StringUtils.hasText(t.getDescription())) {
                    fn.put("description", t.getDescription());
                }
                JsonNode params = t.getParametersSchema();
                if (params != null && !params.isNull()) {
                    fn.set("parameters", params);
                }
                toolsNode.add(tool);
            }
        }

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new PiModelIoException("payload serialization failed", e);
        }
    }

    private ObjectNode buildMessage(Message msg) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", msg.getRole() != null ? msg.getRole() : "user");
        if (StringUtils.hasText(msg.getToolCallId())) {
            m.put("tool_call_id", msg.getToolCallId());
        }
        List<ToolCallEntry> calls = msg.getToolCalls();
        if (calls != null && !calls.isEmpty()) {
            ArrayNode arr = m.putArray("tool_calls");
            for (ToolCallEntry c : calls) {
                if (c == null) {
                    continue;
                }
                ObjectNode call = mapper.createObjectNode();
                call.put("id", c.getId() != null ? c.getId() : "");
                call.put("type", "function");
                ObjectNode fn = call.putObject("function");
                fn.put("name", c.getToolName() != null ? c.getToolName() : "");
                fn.put("arguments", c.getArguments() == null ? "{}" : c.getArguments().toString());
                arr.add(call);
            }
        }
        if (msg.hasImagePart() || (msg.hasParts() && hasNonText(msg.getParts()))) {
            ArrayNode contentArr = m.putArray("content");
            for (ContentPart p : msg.getParts()) {
                if (p == null) {
                    continue;
                }
                contentArr.add(buildPart(p));
            }
        } else {
            m.put("content", msg.getContent() != null ? msg.getContent() : concatText(msg.getParts()));
        }
        return m;
    }

    private ObjectNode buildPart(ContentPart p) {
        ObjectNode node = mapper.createObjectNode();
        if (p.isImageUrl()) {
            node.put("type", "image_url");
            ObjectNode imageUrl = node.putObject("image_url");
            imageUrl.put("url", p.getUrl() != null ? p.getUrl() : "");
            if (p.getDetail() != null) {
                imageUrl.put("detail", p.getDetail());
            }
        } else {
            node.put("type", "text");
            node.put("text", p.getText() != null ? p.getText() : "");
        }
        return node;
    }

    private static boolean hasNonText(List<ContentPart> parts) {
        if (parts == null) {
            return false;
        }
        for (ContentPart p : parts) {
            if (p != null && !p.isText()) {
                return true;
            }
        }
        return false;
    }

    private static String concatText(List<ContentPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentPart p : parts) {
            if (p != null && p.isText() && p.getText() != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(p.getText());
            }
        }
        return sb.toString();
    }
}

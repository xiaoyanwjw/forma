package com.xmut.forma.pi.ai.message;

import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Hermes 轻量消息（对齐 OpenAI chat message 形状的子集）。
 *
 * <p>本模块自有类型。
 *
 * <p>纯文本：{@code content}；多模态：可选 {@code parts}（text / image_url）。
 * 二者可并存；有 parts 时 Provider 应优先按 parts 序列化。
 */
@Value
@Builder(toBuilder = true)
public class Message {

    /**
     * OpenAI 协议角色：system / user / assistant / tool。
     */
    String role;

    /**
     * 纯文本内容（兼容路径）。
     */
    String content;

    /**
     * 多模态内容块；可空。纯文本路径保持 empty。
     */
    @Builder.Default
    List<ContentPart> parts = Collections.emptyList();

    /**
     * tool 角色消息关联的调用 id（对齐 {@code tool_call_id}）；可空。
     */
    String toolCallId;

    /**
     * assistant 角色携带的结构化 tool_calls；可空。
     */
    @Builder.Default
    List<ToolCallEntry> toolCalls = Collections.emptyList();

    Message(String role,
            String content,
            List<ContentPart> parts,
            String toolCallId,
            List<ToolCallEntry> toolCalls) {
        this.role = role;
        this.content = content;
        this.parts = parts == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(parts));
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    // —— 工厂（仍是扁平 Message + role，非类型树）——

    public static Message system(String content) {
        return Message.builder().role("system").content(content != null ? content : "").build();
    }

    public static Message user(String content) {
        return Message.builder().role("user").content(content != null ? content : "").build();
    }

    /** 多模态 user：parts = text + image_url 等。 */
    public static Message user(List<ContentPart> parts) {
        String text = firstText(parts);
        return Message.builder()
                .role("user")
                .content(text)
                .parts(parts != null ? parts : Collections.emptyList())
                .build();
    }

    public static Message assistant(String content, List<ToolCallEntry> toolCalls) {
        return Message.builder()
                .role("assistant")
                .content(content != null ? content : "")
                .toolCalls(toolCalls != null ? toolCalls : Collections.emptyList())
                .build();
    }

    public static Message tool(String callId, String content) {
        return Message.builder()
                .role("tool")
                .content(content != null ? content : "")
                .toolCallId(callId)
                .build();
    }

    /** 是否含非空 parts。 */
    public boolean hasParts() {
        return parts != null && !parts.isEmpty();
    }

    /** 是否含 image_url part。 */
    public boolean hasImagePart() {
        if (!hasParts()) {
            return false;
        }
        for (ContentPart p : parts) {
            if (p != null && p.isImageUrl() && StringUtils.hasText(p.getUrl())) {
                return true;
            }
        }
        return false;
    }

    /** 可投递内容：有文本 content 或非空 parts。 */
    public boolean hasDeliverableContent() {
        return StringUtils.hasText(content) || hasParts();
    }

    /** 从 state 的 {@code MESSAGES} 值拷贝可变列表。 */
    public static List<Message> copyFrom(Object raw) {
        if (!(raw instanceof List)) {
            return new ArrayList<>();
        }
        List<Message> out = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof Message) {
                out.add((Message) item);
            }
        }
        return out;
    }

    /**
     * 幂等 append：已存在相同 user/human/tool(callId) 则跳过。
     * 空白 content 且无 parts 的 user/human 不写入。
     */
    public static void append(List<Message> messages,
                              Message userMessage,
                              List<ToolResult> toolResults,
                              Message humanInput) {
        if (Objects.nonNull(userMessage)
                && userMessage.hasDeliverableContent()
                && !containsUser(messages, userMessage)) {
            messages.add(userMessage);
        }
        Set<String> existingToolIds = toolCallIds(messages);
        for (Message toolMsg : tools(toolResults)) {
            if (!existingToolIds.contains(toolMsg.getToolCallId())) {
                messages.add(toolMsg);
                existingToolIds.add(toolMsg.getToolCallId());
            }
        }
        if (Objects.nonNull(humanInput)
                && humanInput.hasDeliverableContent()
                && !containsUser(messages, humanInput)) {
            messages.add(humanInput);
        }
    }

    /** 把 tool 结果幂等写入 transcript（对齐开源 pi：执行后立刻进 messages）。 */
    public static List<Message> withToolResults(Object messagesRaw, List<ToolResult> toolResults) {
        List<Message> messages = copyFrom(messagesRaw);
        append(messages, null, toolResults, null);
        return messages;
    }


    /** ToolResult → role=tool messages; skip null / blank callId. */
    public static List<Message> tools(List<ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> messages = new ArrayList<>();
        for (ToolResult tr : toolResults) {
            if (tr == null || !org.springframework.util.StringUtils.hasText(tr.getCallId())) {
                continue;
            }
            String content = tr.isSuccess()
                    ? (tr.getOutput() != null ? tr.getOutput() : "")
                    : ("ERROR: " + (tr.getErrorMessage() != null ? tr.getErrorMessage() : "unknown"));
            messages.add(Message.tool(tr.getCallId(), content));
        }
        return messages;
    }

    private static boolean containsUser(List<Message> messages, Message candidate) {
        String content = trimOrEmpty(candidate != null ? candidate.getContent() : null);
        boolean candidateHasImage = candidate != null && candidate.hasImagePart();
        for (Message m : messages) {
            if (m == null || !"user".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            if (candidateHasImage && m.hasImagePart()) {
                // 同轮多模态：按文本+是否含图粗去重
                if (content.equals(trimOrEmpty(m.getContent()))) {
                    return true;
                }
            } else if (!candidateHasImage
                    && StringUtils.hasText(content)
                    && content.equals(trimOrEmpty(m.getContent()))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> toolCallIds(List<Message> messages) {
        Set<String> ids = new HashSet<>();
        for (Message m : messages) {
            if (m != null
                    && "tool".equalsIgnoreCase(m.getRole())
                    && StringUtils.hasText(m.getToolCallId())) {
                ids.add(m.getToolCallId());
            }
        }
        return ids;
    }

    private static String firstText(List<ContentPart> parts) {
        if (parts == null) {
            return "";
        }
        for (ContentPart p : parts) {
            if (p != null && p.isText() && p.getText() != null) {
                return p.getText();
            }
        }
        return "";
    }

    private static String trimOrEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }
}

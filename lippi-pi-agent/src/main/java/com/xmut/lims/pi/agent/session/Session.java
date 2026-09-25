package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 产品 Session 实体（≠ Graph Checkpoint）。
 *
 * <p>{@code messages} 为投影视图（兼容旧 {@link SessionStore#save}/{@link SessionStore#find}）；
 * 真 transcript 行带 seq/runId，见 Store 实现。主键仅为 {@code sessionId}。
 */
@Value
@Builder(toBuilder = true)
public class Session {

    String sessionId;

    @Builder.Default
    List<Message> messages = Collections.emptyList();

    Instant createdAt;
    Instant updatedAt;

    /** hydrate：{@code load} 仅返回 {@code seq > compactAnchorSeq}。 */
    @Builder.Default
    long compactAnchorSeq = 0L;

    /** 最近一轮 runId（可选；对齐 51-17）。 */
    String lastRunId;

    /** 冗余计数（可选；对齐 51-17）。 */
    @Builder.Default
    int messageCount = 0;

    /** {@code api} / {@code cli} / …；默认 api。 */
    @Builder.Default
    String source = "api";

    /** 父会话 ID（可空；仅存字符串，不强制 FK）。 */
    String parentSessionId;

    /** 会话标题（可空）。 */
    String title;

    Session(String sessionId,
            List<Message> messages, Instant createdAt, Instant updatedAt,
            long compactAnchorSeq, String lastRunId, int messageCount, String source,
            String parentSessionId, String title) {
        this.sessionId = sessionId;
        this.messages = messages == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.compactAnchorSeq = compactAnchorSeq;
        this.lastRunId = lastRunId;
        this.messageCount = messageCount;
        this.source = source != null ? source : "api";
        this.parentSessionId = parentSessionId;
        this.title = title;
    }

    /**
     * 本轮 user：
     * <ol>
     *   <li>messages 含 image → 保留多模态 user（OCR）</li>
     *   <li>有 text（含展开后）→ {@code Message.user(expandedText)}</li>
     *   <li>否则 messages 中的 user</li>
     * </ol>
     */
    public static List<Message> resolveThisTurnUser(PromptRequest request, String expandedText) {
        if (request == null) {
            return Collections.emptyList();
        }
        if (hasImageParts(request.getMessages())) {
            return extractUsers(rewriteUserHistory(
                    request.getMessages(), expandedText, request.getText()));
        }
        if (StringUtils.hasText(expandedText)) {
            return Collections.singletonList(Message.user(expandedText));
        }
        if (!CollectionUtils.isEmpty(request.getMessages())) {
            return extractUsers(rewriteUserHistory(
                    request.getMessages(), expandedText, request.getText()));
        }
        return Collections.emptyList();
    }

    /** Store 投影 + 本轮 user（完整 chat 轴）。 */
    public static List<Message> merge(List<Message> storeHistory, List<Message> thisTurnUser) {
        List<Message> merged = new ArrayList<>();
        if (storeHistory != null) {
            merged.addAll(storeHistory);
        }
        if (thisTurnUser != null) {
            merged.addAll(thisTurnUser);
        }
        return merged;
    }

    /**
     * 相对 hydrate 基线取后缀差集；过滤 system。
     * 前缀不匹配（压缩改写）→ {@code null}，调用方应 fork 新 session。
     */
    public static List<Message> computeAppendDelta(List<Message> base, List<Message> delta) {
        List<Message> result = delta != null ? delta : Collections.emptyList();
        int prefix = base != null ? base.size() : 0;
        if (prefix == 0) {
            return filterNonSystem(result);
        }
        if (result.size() >= prefix && prefixMatches(base, result)) {
            return filterNonSystem(result.subList(prefix, result.size()));
        }
        return null;
    }

    public static List<Message> filterNonSystem(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> out = new ArrayList<>();
        for (Message m : messages) {
            if (m != null && !"system".equalsIgnoreCase(m.getRole())) {
                out.add(m);
            }
        }
        return out;
    }

    /** 展开后的文本写入本 turn user；保留 OCR image parts。 */
    public static List<Message> rewriteUserHistory(List<Message> messages,
                                                   String expandedText,
                                                   String originalText) {
        if (CollectionUtils.isEmpty(messages)) {
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(expandedText) || Objects.equals(expandedText, originalText)) {
            return messages;
        }

        List<Message> out = new ArrayList<>(messages.size());
        boolean replaced = false;
        for (Message m : messages) {
            if (!replaced && m != null && "user".equalsIgnoreCase(m.getRole())
                    && !m.hasImagePart()
                    && textEqualsTrimmed(originalText, m.getContent())) {
                out.add(Message.user(expandedText));
                replaced = true;
            } else {
                out.add(m);
            }
        }
        return out;
    }

    public static boolean hasImageParts(List<Message> messages) {
        if (messages == null) {
            return false;
        }
        for (Message m : messages) {
            if (m != null && m.hasImagePart()) {
                return true;
            }
        }
        return false;
    }

    static boolean prefixMatches(List<Message> prefix, List<Message> full) {
        for (int i = 0; i < prefix.size(); i++) {
            if (!Objects.equals(prefix.get(i), full.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<Message> extractUsers(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> users = new ArrayList<>();
        for (Message m : messages) {
            if (m != null && "user".equalsIgnoreCase(m.getRole())) {
                users.add(m);
            }
        }
        return users;
    }

    private static boolean textEqualsTrimmed(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equals(right.trim());
    }

    @Value
    @Builder(toBuilder = true)
    public static class Meta {

        String sessionId;

        /** 可空；Store 默认 {@code "api"}（CLI 传 {@code "cli"}）。 */
        String source;

        /** 可空；父会话 ID（仅存字符串）。 */
        String parentSessionId;

        /** 可空；会话标题。 */
        String title;
    }

}

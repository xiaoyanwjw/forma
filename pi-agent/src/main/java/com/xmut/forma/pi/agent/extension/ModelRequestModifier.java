package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.ai.message.Message;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 进模型前对消息的修改量。
 * 功能描述：把 prefix 贴到最后一条 user。
 * 关键设计：总线只传递本对象，不自己拼接；不管这一轮的 extension 应返回 null 而不是 {@link #empty()}。
 */
public final class ModelRequestModifier {

    private static final String REMINDER_OPEN = "<reminder>";

    private final String lastUserPrefix;

    public static ModelRequestModifier empty() {
        return new ModelRequestModifier(null);
    }

    public ModelRequestModifier(String lastUserPrefix) {
        this.lastUserPrefix = lastUserPrefix;
    }

    /** 仅 apply / 测试用；总线不拼接。 */
    public String getLastUserPrefix() {
        return lastUserPrefix;
    }

    /**
     * 从后往前找第一条 user，把 prefix 接到其 content 前。
     * 空白 prefix、没有 user、或该 content trim 后已以 {@code <reminder>} 开头时原样返回。
     */
    public List<Message> apply(List<Message> messages) {
        if (messages == null || messages.isEmpty() || !StringUtils.hasText(lastUserPrefix)) {
            return messages;
        }
        int index = lastUserIndex(messages);
        if (index < 0) {
            return messages;
        }
        Message original = messages.get(index);
        String content = original.getContent();
        if (content != null && content.trim().startsWith(REMINDER_OPEN)) {
            return messages;
        }
        String prefixed = lastUserPrefix + (content == null ? "" : content);
        List<Message> copy = new ArrayList<Message>(messages);
        copy.set(index, original.toBuilder().content(prefixed).build());
        return copy;
    }

    private static int lastUserIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                return i;
            }
        }
        return -1;
    }
}

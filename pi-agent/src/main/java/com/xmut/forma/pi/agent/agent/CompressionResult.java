package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * ContextCompressor 输出。
 * 功能描述：返回压缩后的 messages / context 视图。
 */
@Value
@Builder(toBuilder = true)
public class CompressionResult {

    @Builder.Default
    List<Message> messages = Collections.emptyList();

    /** Context 被改写后的 system 原料；未改则为 null（沿用原 input）。 */
    SystemPromptInput systemInput;

    /** 是否改动了 Context 段（需 invalidate + rebuild system）。 */
    @Builder.Default
    boolean contextChanged = false;

    /** 是否执行了任何压缩（history 或 Context）。 */
    @Builder.Default
    boolean compressed = false;

    public List<Message> getMessages() {
        return messages != null ? messages : Collections.emptyList();
    }

    public static CompressionResult unchanged(CompressionRequest request) {
        List<Message> msgs = request != null ? request.getMessages() : Collections.emptyList();
        return CompressionResult.builder()
                .messages(msgs)
                .systemInput(request != null ? request.getSystemInput() : null)
                .contextChanged(false)
                .compressed(false)
                .build();
    }
}

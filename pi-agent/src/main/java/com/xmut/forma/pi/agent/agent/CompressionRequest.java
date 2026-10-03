package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.ai.message.Message;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * ContextCompressor 输入。
 * 功能描述：携带发模型前的 messages 视图。
 */
@Value
@Builder(toBuilder = true)
public class CompressionRequest {

    /** append 后的 transcript（不含 system）。 */
    @Builder.Default
    List<Message> messages = Collections.emptyList();

    /** 可选；用于 Context 段压缩。 */
    SystemPromptInput systemInput;

    /** 当前已构建的 system 文本（估阈值用）；可空。 */
    Message systemMessage;

    String sessionId;

    String runId;

    public List<Message> getMessages() {
        return messages != null ? messages : Collections.emptyList();
    }
}

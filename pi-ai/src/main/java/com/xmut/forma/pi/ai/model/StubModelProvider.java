package com.xmut.forma.pi.ai.model;

import com.xmut.forma.pi.ai.message.Message;

import java.util.Collections;
import java.util.List;

/**
 * 无 API Key 时的默认 Provider：回显最后一条 user 消息；不产生 toolCalls。
 *
 * <p>不发 HTTP；可被 {@code @ConditionalOnMissingBean(ModelProvider.class)} 装配。
 */
public final class StubModelProvider implements ModelProvider {

    @Override
    public ModelResponse complete(ModelRequest request) {
        String content = resolveLastUserContent(request != null ? request.getMessages() : null);
        return ModelResponse.builder()
                .content(content)
                .toolCalls(Collections.emptyList())
                .finishReason("stop")
                .modelVersion("stub")
                .build();
    }

    static String resolveLastUserContent(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m == null || !"user".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            if (m.getContent() != null && !m.getContent().isEmpty()) {
                return m.getContent();
            }
            if (m.hasParts()) {
                for (com.xmut.forma.pi.ai.message.ContentPart p : m.getParts()) {
                    if (p != null && p.isText() && p.getText() != null) {
                        return p.getText();
                    }
                }
            }
        }
        return null;
    }
}

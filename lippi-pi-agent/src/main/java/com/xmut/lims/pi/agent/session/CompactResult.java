package com.xmut.lims.pi.agent.session;

import lombok.Builder;
import lombok.Value;

/**
 * {@link AgentSession#compact} 出参（M1 端口形状；实现可桩）。
 */
@Value
@Builder
public class CompactResult {

    String sessionId;
    boolean compacted;
    String message;

    public static CompactResult noop(String sessionId) {
        return noop(sessionId, "锚点 API 已就绪（SessionStore.setCompactAnchor），CLI /compact 接线可后置");
    }

    public static CompactResult noop(String sessionId, String message) {
        return CompactResult.builder()
                .sessionId(sessionId)
                .compacted(false)
                .message(message != null ? message
                        : "锚点 API 已就绪（SessionStore.setCompactAnchor），CLI /compact 接线可后置")
                .build();
    }
}

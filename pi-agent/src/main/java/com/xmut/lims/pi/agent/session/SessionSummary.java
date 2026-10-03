package com.xmut.lims.pi.agent.session;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 会话轻量摘要。
 * 功能描述：供 listRecent / listChildren 使用，不含 transcript 消息。
 */
@Value
@Builder
public class SessionSummary {

    String sessionId;
    String parentSessionId;
    String title;
    String source;
    String status;
    int messageCount;
    Instant updatedAt;
    String lastRunId;
}

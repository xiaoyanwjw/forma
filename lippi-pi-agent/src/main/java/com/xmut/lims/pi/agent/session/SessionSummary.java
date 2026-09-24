package com.xmut.lims.pi.agent.session;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/** 会话列表/树用轻量投影（不含 transcript 消息）。 */
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

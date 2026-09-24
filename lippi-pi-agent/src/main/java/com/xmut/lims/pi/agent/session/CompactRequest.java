package com.xmut.lims.pi.agent.session;

import lombok.Builder;
import lombok.Value;

/**
 * {@link AgentSession#compact} 入参（M1 端口形状；实现可桩）。
 */
@Value
@Builder
public class CompactRequest {
    String sessionId;
    String reason;
}

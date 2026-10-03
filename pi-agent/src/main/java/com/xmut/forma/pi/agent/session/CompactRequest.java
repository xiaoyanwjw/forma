package com.xmut.forma.pi.agent.session;

import lombok.Builder;
import lombok.Value;

/**
 * AgentSession.compact 入参。
 * 功能描述：指定要压缩的会话；实现当前可为 NOOP。
 */
@Value
@Builder
public class CompactRequest {
    String sessionId;
    String reason;
}

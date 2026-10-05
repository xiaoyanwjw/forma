package com.xmut.forma.pi.agent.extension;

import lombok.Builder;
import lombok.Value;

/**
 * before_agent_start 事件载荷。
 * 功能描述：在 slash 展开之后、入图之前发出。
 */
@Value
@Builder
public class BeforeAgentStartEvent {

    String runId;
    String skillId;
    String workspaceRoot;
    String userText;
    String pageContext;
}

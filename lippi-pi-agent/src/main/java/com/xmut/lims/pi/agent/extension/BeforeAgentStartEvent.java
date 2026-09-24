package com.xmut.lims.pi.agent.extension;

import lombok.Value;

/**
 * {@code before_agent_start} 事件：展开之后、入图之前。
 */
@Value
public class BeforeAgentStartEvent {

    String runId;
    String userText;
    String pageContext;
}

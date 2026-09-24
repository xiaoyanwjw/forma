package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.session.TurnResult;
import lombok.Value;

/**
 * {@code agent_end} 事件：prompt / resume 返回前。不实现 51-11 指标。
 */
@Value
public class AgentEndEvent {

    String runId;
    TurnResult result;
}

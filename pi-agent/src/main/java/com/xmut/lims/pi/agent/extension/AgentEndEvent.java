package com.xmut.lims.pi.agent.extension;

import com.xmut.lims.pi.agent.session.TurnResult;
import lombok.Value;

/**
 * agent_end 事件载荷。
 * 功能描述：在 prompt / resume 返回前发出。
 */
@Value
public class AgentEndEvent {

    String runId;
    TurnResult result;
}

package com.xmut.ebus.application.business.agent.sse;

/**
 * AD-4 闭合 SSE 事件名（禁止同义别名）。
 */
public enum Ad4EventName {

    run_started,
    agent_started,
    message_delta,
    tool_started,
    tool_finished,
    agent_ended,
    artifact_ready,
    run_failed,
    run_settled;

    public String wireName() {
        return name();
    }
}

package com.xmut.ebus.application.business.agent.sse;

/**
 * AD-4 闭合 SSE 事件名（禁止同义别名）。
 * <p>
 * 枚举常量全大写下划线；{@link #wireName()} 为对外 SSE {@code event:} 名（小写下划线，与 FE 对齐）。
 */
public enum Ad4EventName {

    RUN_STARTED("run_started"),
    AGENT_STARTED("agent_started"),
    MESSAGE_DELTA("message_delta"),
    TOOL_STARTED("tool_started"),
    TOOL_FINISHED("tool_finished"),
    AGENT_ENDED("agent_ended"),
    HUMAN_INPUT_REQUIRED("human_input_required"),
    ARTIFACT_READY("artifact_ready"),
    RUN_FAILED("run_failed"),
    RUN_SETTLED("run_settled");

    private final String wireName;

    Ad4EventName(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}

package com.xmut.lims.pi.agent.event;

/**
 * 生命周期事件类型枚举。
 * 功能描述：定义经 PiEventBus 发出的事件名。
 * 关键设计：仅 COMMAND / BEFORE_AGENT_START / BEFORE_TOOL_CALL / AFTER_TOOL_CALL 可 on 归约。
 */
public enum PiEventType {
    AGENT_START,
    AGENT_END,
    TURN_START,
    TURN_END,
    MESSAGE_START,
    MESSAGE_UPDATE,
    MESSAGE_END,
    TOOL_EXECUTION_START,
    TOOL_EXECUTION_UPDATE,
    TOOL_EXECUTION_END,
    SUSPENDED,
    COMMAND,
    BEFORE_AGENT_START,
    BEFORE_TOOL_CALL,
    AFTER_TOOL_CALL
}

package com.xmut.lims.pi.agent.event;

/**
 * Lifecycle facts emitted through {@link PiEventBus}.
 *
 * <p>Only {@link #COMMAND}, {@link #BEFORE_AGENT_START}, {@link #BEFORE_TOOL_CALL},
 * and {@link #AFTER_TOOL_CALL} are reducible via {@code on} handlers.
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

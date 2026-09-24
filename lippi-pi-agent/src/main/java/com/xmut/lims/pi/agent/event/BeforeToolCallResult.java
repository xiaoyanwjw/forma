package com.xmut.lims.pi.agent.event;

import lombok.Value;

/**
 * Reduced result of {@link PiEventType#BEFORE_TOOL_CALL}.
 *
 * <p>{@code block} / {@code needsHitl} short-circuit remaining {@code on} handlers.
 */
@Value
public class BeforeToolCallResult {

    public enum Kind {
        ALLOW,
        BLOCK,
        NEEDS_HITL
    }

    Kind kind;
    String reason;

    public static BeforeToolCallResult allow() {
        return new BeforeToolCallResult(Kind.ALLOW, null);
    }

    public static BeforeToolCallResult block(String reason) {
        return new BeforeToolCallResult(Kind.BLOCK, reason);
    }

    public static BeforeToolCallResult needsHitl(String reason) {
        return new BeforeToolCallResult(Kind.NEEDS_HITL, reason);
    }

    public boolean isAllow() {
        return kind == Kind.ALLOW;
    }

    public boolean isBlock() {
        return kind == Kind.BLOCK;
    }

    public boolean isNeedsHitl() {
        return kind == Kind.NEEDS_HITL;
    }
}

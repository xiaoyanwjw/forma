package com.xmut.forma.pi.agent.event;

import lombok.Value;

/**
 * BEFORE_TOOL_CALL 归约结果。
 * 功能描述：表达 allow / block / needs_hitl。
 * 关键设计：block 与 needs_hitl 会短路后续 on 处理器。
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

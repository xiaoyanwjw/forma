package com.xmut.lims.pi.agent.event;

import lombok.Value;

/**
 * 不可变生命周期事件。
 * 功能描述：携带类型、可选 payload 与 turnId。
 */
@Value
public class PiEvent {

    PiEventType type;
    Object payload;
    /** Nullable: same id for one agent turn and the tools it requested. */
    String turnId;

    public static PiEvent of(PiEventType type) {
        return of(type, null, null);
    }

    public static PiEvent of(PiEventType type, Object payload) {
        return of(type, payload, null);
    }

    public static PiEvent of(PiEventType type, Object payload, String turnId) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        return new PiEvent(type, payload, turnId);
    }
}

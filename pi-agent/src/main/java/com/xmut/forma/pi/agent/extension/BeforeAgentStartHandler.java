package com.xmut.forma.pi.agent.extension;

import com.xmut.forma.pi.agent.event.PiEvent;

/**
 * BEFORE_AGENT_START 累加处理器。
 * 功能描述：按注册顺序改同一份 {@link ContextModifier}，不返回归约值。
 */
@FunctionalInterface
public interface BeforeAgentStartHandler {

    void apply(ContextModifier modifier, PiEvent event);
}

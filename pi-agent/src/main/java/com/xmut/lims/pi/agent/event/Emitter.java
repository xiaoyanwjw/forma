package com.xmut.lims.pi.agent.event;

/**
 * 窄口事件发射端口。
 * 功能描述：供图节点 / Agent 发事件，不暴露 subscribe。
 */
public interface Emitter {

    /** 发给全部 observe / on（无归约结果）。 */
    void emit(PiEvent event);

    /** 同上，并按 {@code resultType} 归约 on 的返回值。 */
    <T> T emit(PiEvent event, Class<T> resultType);
}

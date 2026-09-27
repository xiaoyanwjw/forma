package com.xmut.ebus.application.business.agent.support;

/**
 * {@code streamBilledRun} 拦截器：prompt 前 / view 后（persist 前）可挂钩。
 * 默认空实现；抛 {@code BusinessException} 则失败 release。
 */
public interface BilledRunInterceptor {

    /** 在 {@code AgentSession.prompt} 之前。 */
    default void before(BilledRunContext ctx) {
        // no-op
    }

    /**
     * Computer view 投影成功之后、persist 之前。
     * 可改写 {@link BilledRunContext#getProjectedView()} /
     * {@link BilledRunContext#getBusinessPayload()}。
     */
    default void after(BilledRunContext ctx) {
        // no-op
    }
}

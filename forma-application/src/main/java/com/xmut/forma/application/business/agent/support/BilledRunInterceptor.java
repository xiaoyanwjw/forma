package com.xmut.forma.application.business.agent.support;

/**
 * Billed 管线前后拦截：只负责「跑模型前 / 投影后·落库前」两刀。
 * <p>
 * 抛 {@code BusinessException} 由 stream 捕获后 release + markFailed + emit。
 * 流式事件见 {@link BilledRunListener}；SUSPENDED 见 {@link BilledSuspendedHandler}。
 */
public interface BilledRunInterceptor {

    /**
     * prompt / resume 之前。
     * Resume 时可读 {@link BilledRunContext#getResumeOptionId()}。
     */
    default void onBefore(BilledRunContext ctx) {
        // no-op
    }

    /**
     * Computer view 投影成功之后、persist 之前。
     * 可改写 projectedView / businessPayload / persistAs。
     */
    default void onAfter(BilledRunContext ctx) {
        // no-op
    }
}

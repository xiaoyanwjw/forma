package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.agent.sse.SseEvent;

/**
 * Billed 流式 AD-4 事件旁路（emit 前）。
 * 与 {@link BilledRunInterceptor} 分离：Listener 观察，Interceptor 改管道状态。
 */
public interface BilledRunListener {

    default void onEvent(BilledRunContext ctx, SseEvent event) {
        // no-op
    }
}

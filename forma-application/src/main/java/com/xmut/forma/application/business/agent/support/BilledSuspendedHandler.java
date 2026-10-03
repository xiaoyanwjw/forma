package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.agent.sse.SseEvent;

import java.util.function.Consumer;

/**
 * Turn {@code SUSPENDED} 业务副作用（persist / echo 等）。
 * <p>
 * 挂起真源在 Checkpoint。管线会依次调用全部 handler，再由
 * {@code settleOnSuspended} / {@code releaseOnSuspended} 做计费收尾。
 */
public interface BilledSuspendedHandler {

    boolean onSuspended(BilledRunContext ctx, Consumer<SseEvent> sink);
}

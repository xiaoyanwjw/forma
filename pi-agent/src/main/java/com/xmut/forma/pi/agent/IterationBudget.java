package com.xmut.forma.pi.agent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 超步预算计数器。
 * 功能描述：通过 consume / refund 限制 StateGraph 超步次数，并可带整体超时。
 */
public final class IterationBudget {

    private final int maxTotal;
    private final AtomicInteger used;
    /** 整体超时（毫秒）；null 时 Loop 使用默认 300s。 */
    private final Long overallTimeoutMs;

    public IterationBudget(int maxTotal) {
        this(maxTotal, null);
    }

    public IterationBudget(int maxTotal, Long overallTimeoutMs) {
        if (maxTotal < 1) {
            throw new IllegalArgumentException("maxTotal must be >= 1, got: " + maxTotal);
        }
        this.maxTotal = maxTotal;
        this.used = new AtomicInteger(0);
        this.overallTimeoutMs = overallTimeoutMs;
    }

    /**
     * 尝试消耗 1 个 iteration（= 1 个超步）。超限返回 {@code false}。
     */
    public boolean consume() {
        while (true) {
            int current = used.get();
            if (current >= maxTotal) {
                return false;
            }
            if (used.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /**
     * 退还 1 个已消耗的 iteration；{@code used} 不会变为负数。
     */
    public void refund() {
        while (true) {
            int current = used.get();
            if (current <= 0) {
                return;
            }
            if (used.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    public int used() {
        return used.get();
    }

    public int remaining() {
        return Math.max(0, maxTotal - used.get());
    }

    public int maxTotal() {
        return maxTotal;
    }

    public Long overallTimeoutMs() {
        return overallTimeoutMs;
    }
}

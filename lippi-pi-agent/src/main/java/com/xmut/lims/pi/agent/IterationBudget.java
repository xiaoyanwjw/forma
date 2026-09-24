package com.xmut.lims.pi.agent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对齐 Hermes {@code IterationBudget(max_total)}：可变计数器，支持 {@link #consume} / {@link #refund}。
 *
 * <p>LIMS 语义：一次 {@code consume} = 一次 StateGraph 超步（NFR9 {@code maxSupersteps} 语义）。
 * 配置侧仍可称 maxSupersteps；本类型字段名固定为 {@code maxTotal}。
 *
 * <p>{@code overallTimeoutMs} 由 Loop 映射为 {@code CompileConfig.overallTimeout}，
 * 在 GraphExecutor 超步边界强制检查。
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

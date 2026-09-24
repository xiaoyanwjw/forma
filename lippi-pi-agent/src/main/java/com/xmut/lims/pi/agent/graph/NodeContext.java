package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.event.Emitter;

/**
 * 节点执行上下文（不可变）。不携带 agent Capability / ToolExecutor。
 *
 * <p>身份仅 {@code runId} + 可选 {@code traceId}；不携带 tenant / user。
 * <p>生命周期事实只经 {@link Emitter}；禁止依赖 {@code StreamObserver}。
 */
public final class NodeContext {

    private final String runId;
    private final String traceId;
    private final Emitter emitter;

    public NodeContext(String runId, String traceId) {
        this(runId, traceId, null);
    }

    public NodeContext(String runId, String traceId, Emitter emitter) {
        this.runId = runId;
        this.traceId = traceId;
        this.emitter = emitter;
    }

    public String getRunId() {
        return runId;
    }

    public String getTraceId() {
        return traceId;
    }

    /** 窄口 emit；非订阅 / 单测路径可为 null。 */
    public Emitter getEmitter() {
        return emitter;
    }
}

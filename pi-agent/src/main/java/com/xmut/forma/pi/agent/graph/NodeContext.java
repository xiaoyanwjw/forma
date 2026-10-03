package com.xmut.forma.pi.agent.graph;

import com.xmut.forma.pi.agent.event.Emitter;

/**
 * 节点执行上下文。
 * 功能描述：向节点提供只读运行信息与 Emitter 等协作面。
 * 关键设计：不携带 Capability / ToolExecutor。
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

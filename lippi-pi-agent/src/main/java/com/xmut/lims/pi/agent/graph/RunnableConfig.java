package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.event.Emitter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 图执行运行时配置；携带 cancel 信号供 Executor 在超步边界检查。
 *
 * <p>身份仅 {@code runId} + 可选 {@code traceId}；不携带 tenant / user。
 */
public final class RunnableConfig {

    private final String runId;
    private final String traceId;
    private final Map<String, Object> metadata;
    private final BooleanSupplier cancelSignal;
    private final AtomicReference<String> cancelReason;
    private final Emitter emitter;

    private RunnableConfig(Builder builder) {
        this.runId = builder.runId;
        this.traceId = builder.traceId;
        this.metadata = builder.metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
                : Collections.emptyMap();
        this.cancelSignal = builder.cancelSignal != null ? builder.cancelSignal : () -> false;
        this.cancelReason = builder.cancelReason != null ? builder.cancelReason : new AtomicReference<>();
        this.emitter = builder.emitter;
    }

    public static RunnableConfig of(String runId, String traceId) {
        return builder().runId(runId).traceId(traceId).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRunId() {
        return runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isCancelRequested() {
        return cancelSignal.getAsBoolean();
    }

    public String getCancelReason() {
        return cancelReason.get();
    }

    public AtomicReference<String> getCancelReasonRef() {
        return cancelReason;
    }

    /** 窄口 emit；非订阅路径可为 null。 */
    public Emitter getEmitter() {
        return emitter;
    }

    public static final class Builder {
        private String runId;
        private String traceId;
        private Map<String, Object> metadata;
        private BooleanSupplier cancelSignal;
        private AtomicReference<String> cancelReason;
        private Emitter emitter;

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder cancelSignal(BooleanSupplier cancelSignal) {
            this.cancelSignal = cancelSignal;
            return this;
        }

        public Builder cancelReason(AtomicReference<String> cancelReason) {
            this.cancelReason = cancelReason;
            return this;
        }

        public Builder emitter(Emitter emitter) {
            this.emitter = emitter;
            return this;
        }

        public RunnableConfig build() {
            return new RunnableConfig(this);
        }
    }
}

package com.xmut.lims.pi.agent.graph;

import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图编译配置：maxSupersteps / overallTimeout / interrupt 钩子 / CheckpointStore。
 */
public final class CompileConfig {

    private final Checkpointer checkpointer;
    private final List<String> interruptBefore;
    private final List<String> interruptAfter;
    private final Duration overallTimeout;
    private final int maxSupersteps;

    private CompileConfig(Builder builder) {
        this.checkpointer = builder.checkpointer;
        this.interruptBefore = Collections.unmodifiableList(new ArrayList<>(builder.interruptBefore));
        this.interruptAfter = Collections.unmodifiableList(new ArrayList<>(builder.interruptAfter));
        this.overallTimeout = builder.overallTimeout;
        this.maxSupersteps = builder.maxSupersteps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Checkpointer getCheckpointer() {
        return checkpointer;
    }

    public List<String> getInterruptBefore() {
        return interruptBefore;
    }

    public List<String> getInterruptAfter() {
        return interruptAfter;
    }

    public Duration getOverallTimeout() {
        return overallTimeout;
    }

    public int getMaxSupersteps() {
        return maxSupersteps;
    }

    public static final class Builder {
        private Checkpointer checkpointer;
        private List<String> interruptBefore = new ArrayList<>();
        private List<String> interruptAfter = new ArrayList<>();
        private Duration overallTimeout = Duration.ofSeconds(300);
        private int maxSupersteps = 25;

        public Builder checkpointer(Checkpointer checkpointer) {
            this.checkpointer = checkpointer;
            return this;
        }

        public Builder interruptBefore(List<String> nodes) {
            this.interruptBefore = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
            return this;
        }

        public Builder interruptAfter(List<String> nodes) {
            this.interruptAfter = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
            return this;
        }

        public Builder overallTimeout(Duration timeout) {
            this.overallTimeout = timeout;
            return this;
        }

        public Builder maxSupersteps(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("maxSupersteps must be >= 1, got: " + max);
            }
            this.maxSupersteps = max;
            return this;
        }

        public CompileConfig build() {
            return new CompileConfig(this);
        }
    }
}

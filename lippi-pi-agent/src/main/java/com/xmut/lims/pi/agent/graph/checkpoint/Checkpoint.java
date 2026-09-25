package com.xmut.lims.pi.agent.graph.checkpoint;

import com.xmut.lims.pi.agent.graph.GraphState;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 不可变图检查点。
 * 功能描述：记录某超步后的状态快照。
 */
public final class Checkpoint {

    private final String checkpointId;
    private final String runId;
    private final int step;
    private final String currentNode;
    private final GraphState state;
    private final Instant createdAt;
    private final Map<String, Object> metadata;

    public Checkpoint(String checkpointId, String runId,
                      int step, String currentNode, GraphState state,
                      Instant createdAt, Map<String, Object> metadata) {
        this.checkpointId = checkpointId;
        this.runId = runId;
        this.step = step;
        this.currentNode = currentNode;
        this.state = state;
        this.createdAt = createdAt;
        this.metadata = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getRunId() {
        return runId;
    }

    public int getStep() {
        return step;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public GraphState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

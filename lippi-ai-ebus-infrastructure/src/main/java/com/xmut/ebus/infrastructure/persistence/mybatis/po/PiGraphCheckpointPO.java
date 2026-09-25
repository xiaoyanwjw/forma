package com.xmut.ebus.infrastructure.persistence.mybatis.po;

import java.time.Instant;

/**
 * pi_graph_checkpoint 表 PO。
 */
public class PiGraphCheckpointPO {

    private String runId;
    private String checkpointId;
    private String graphState;
    private Instant updatedAt;
    private Instant expiresAt;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getGraphState() {
        return graphState;
    }

    public void setGraphState(String graphState) {
        this.graphState = graphState;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}

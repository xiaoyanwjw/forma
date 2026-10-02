package com.xmut.ebus.domain.business.agent.model;

/** 回放分页：逻辑 runId + 可见区内 MAX(seq)。 */
public final class PiLogicalRunRef {
    private final String logicalRunId;
    private final long tipSeq;

    public PiLogicalRunRef(String logicalRunId, long tipSeq) {
        this.logicalRunId = logicalRunId;
        this.tipSeq = tipSeq;
    }

    public String getLogicalRunId() {
        return logicalRunId;
    }

    public long getTipSeq() {
        return tipSeq;
    }
}

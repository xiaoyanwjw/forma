package com.xmut.ebus.infrastructure.persistence.mybatis.po;

/**
 * Logical run page row: {@code SUBSTRING_INDEX(run_id, ':', 1)} + max seq in that run.
 */
public class PiLogicalRunRow {

    private String logicalRunId;
    private long tipSeq;

    public String getLogicalRunId() {
        return logicalRunId;
    }

    public void setLogicalRunId(String logicalRunId) {
        this.logicalRunId = logicalRunId;
    }

    public long getTipSeq() {
        return tipSeq;
    }

    public void setTipSeq(long tipSeq) {
        this.tipSeq = tipSeq;
    }
}

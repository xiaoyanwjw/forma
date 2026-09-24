package com.xmut.ebus.infrastructure.persistence.mybatis.po;

import java.time.Instant;

/**
 * ebus_credit_tier_change 表 PO。
 */
public class CreditTierChangePO {

    private String id;
    private String accountId;
    private String targetUserId;
    private String operatorUserId;
    private String fromTier;
    private String toTier;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getFromTier() {
        return fromTier;
    }

    public void setFromTier(String fromTier) {
        this.fromTier = fromTier;
    }

    public String getToTier() {
        return toTier;
    }

    public void setToTier(String toTier) {
        this.toTier = toTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

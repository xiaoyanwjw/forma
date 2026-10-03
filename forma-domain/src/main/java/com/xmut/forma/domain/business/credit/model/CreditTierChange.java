package com.xmut.forma.domain.business.credit.model;

import com.xmut.forma.domain.business.credit.constant.CreditTier;

import java.time.Instant;

/**
 * 手工改档审计记录（谁 / 何时 / 从→到 / 目标用户）。
 */
public class CreditTierChange {

    private String id;
    private String accountId;
    private String targetUserId;
    private String operatorUserId;
    private CreditTier fromTier;
    private CreditTier toTier;
    private Instant createdAt;

    public static CreditTierChange create(String id,
                                         String accountId,
                                         String targetUserId,
                                         String operatorUserId,
                                         CreditTier fromTier,
                                         CreditTier toTier,
                                         Instant createdAt) {
        CreditTierChange change = new CreditTierChange();
        change.id = id;
        change.accountId = accountId;
        change.targetUserId = targetUserId;
        change.operatorUserId = operatorUserId;
        change.fromTier = fromTier;
        change.toTier = toTier;
        change.createdAt = createdAt;
        return change;
    }

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

    public CreditTier getFromTier() {
        return fromTier;
    }

    public void setFromTier(CreditTier fromTier) {
        this.fromTier = fromTier;
    }

    public CreditTier getToTier() {
        return toTier;
    }

    public void setToTier(CreditTier toTier) {
        this.toTier = toTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

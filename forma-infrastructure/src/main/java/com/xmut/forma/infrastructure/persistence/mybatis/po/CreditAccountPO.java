package com.xmut.forma.infrastructure.persistence.mybatis.po;

import java.time.Instant;

/**
 * forma_credit_account 表 PO（id=库内自增；bizId=对外 UUID）。
 */
public class CreditAccountPO {

    private Long id;
    private String bizId;
    private String userId;
    private String tier;
    private int balance;
    private int reserved;
    private Instant periodAnchorAt;
    private Instant nextResetAt;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public Instant getPeriodAnchorAt() {
        return periodAnchorAt;
    }

    public void setPeriodAnchorAt(Instant periodAnchorAt) {
        this.periodAnchorAt = periodAnchorAt;
    }

    public Instant getNextResetAt() {
        return nextResetAt;
    }

    public void setNextResetAt(Instant nextResetAt) {
        this.nextResetAt = nextResetAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

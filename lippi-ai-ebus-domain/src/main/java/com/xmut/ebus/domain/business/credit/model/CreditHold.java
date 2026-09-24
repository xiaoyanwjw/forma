package com.xmut.ebus.domain.business.credit.model;

import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;

import java.time.Instant;

/**
 * 积分预占记录（1 次计费动作对应 1 条 hold，默认 amount=1）。
 */
public class CreditHold {

    private String id;
    private String accountId;
    private String userId;
    private int amount;
    private CreditHoldStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static CreditHold createActive(String id, String accountId, String userId, int amount, Instant now) {
        CreditHold hold = new CreditHold();
        hold.id = id;
        hold.accountId = accountId;
        hold.userId = userId;
        hold.amount = amount;
        hold.status = CreditHoldStatus.ACTIVE;
        hold.createdAt = now;
        hold.updatedAt = now;
        return hold;
    }

    public void markSettled(Instant now) {
        this.status = CreditHoldStatus.SETTLED;
        this.updatedAt = now;
    }

    public void markReleased(Instant now) {
        this.status = CreditHoldStatus.RELEASED;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == CreditHoldStatus.ACTIVE;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public CreditHoldStatus getStatus() {
        return status;
    }

    public void setStatus(CreditHoldStatus status) {
        this.status = status;
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

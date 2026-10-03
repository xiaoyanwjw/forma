package com.xmut.forma.application.business.credit.dto;

import java.time.Instant;

/**
 * 当前用户积分摘要（GET /api/v1/credits）。
 */
public class CreditBalanceDTO {

    private String tier;
    private int available;
    private int balance;
    private int reserved;
    private Instant nextResetAt;
    private Instant periodAnchorAt;

    public CreditBalanceDTO() {
    }

    public CreditBalanceDTO(String tier, int available, int balance, int reserved,
                            Instant nextResetAt, Instant periodAnchorAt) {
        this.tier = tier;
        this.available = available;
        this.balance = balance;
        this.reserved = reserved;
        this.nextResetAt = nextResetAt;
        this.periodAnchorAt = periodAnchorAt;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
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

    public Instant getNextResetAt() {
        return nextResetAt;
    }

    public void setNextResetAt(Instant nextResetAt) {
        this.nextResetAt = nextResetAt;
    }

    public Instant getPeriodAnchorAt() {
        return periodAnchorAt;
    }

    public void setPeriodAnchorAt(Instant periodAnchorAt) {
        this.periodAnchorAt = periodAnchorAt;
    }
}

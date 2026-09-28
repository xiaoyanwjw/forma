package com.xmut.ebus.application.business.credit.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 账户页只读用量：摘要 + SETTLED 流水（GET /api/v1/account/credits/usage）。
 */
public class CreditUsageDTO {

    public static final String ENTRY_TITLE_SETTLED = "已扣分";

    private String tier;
    private int available;
    private int monthlyQuota;
    private int used;
    private Instant nextResetAt;
    private List<CreditUsageEntryDTO> entries = new ArrayList<>();

    public CreditUsageDTO() {
    }

    public CreditUsageDTO(String tier, int available, int monthlyQuota, int used,
                          Instant nextResetAt, List<CreditUsageEntryDTO> entries) {
        this.tier = tier;
        this.available = available;
        this.monthlyQuota = monthlyQuota;
        this.used = used;
        this.nextResetAt = nextResetAt;
        this.entries = entries != null ? entries : new ArrayList<>();
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

    public int getMonthlyQuota() {
        return monthlyQuota;
    }

    public void setMonthlyQuota(int monthlyQuota) {
        this.monthlyQuota = monthlyQuota;
    }

    public int getUsed() {
        return used;
    }

    public void setUsed(int used) {
        this.used = used;
    }

    public Instant getNextResetAt() {
        return nextResetAt;
    }

    public void setNextResetAt(Instant nextResetAt) {
        this.nextResetAt = nextResetAt;
    }

    public List<CreditUsageEntryDTO> getEntries() {
        return entries;
    }

    public void setEntries(List<CreditUsageEntryDTO> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    /**
     * 单条已结算扣分流水（人话标题固定「已扣分」）。
     */
    public static class CreditUsageEntryDTO {

        private String holdId;
        private String title;
        private int amount;
        private int delta;
        private Instant occurredAt;

        public CreditUsageEntryDTO() {
        }

        public CreditUsageEntryDTO(String holdId, String title, int amount, int delta, Instant occurredAt) {
            this.holdId = holdId;
            this.title = title;
            this.amount = amount;
            this.delta = delta;
            this.occurredAt = occurredAt;
        }

        public String getHoldId() {
            return holdId;
        }

        public void setHoldId(String holdId) {
            this.holdId = holdId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public int getDelta() {
            return delta;
        }

        public void setDelta(int delta) {
            this.delta = delta;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
        }
    }
}

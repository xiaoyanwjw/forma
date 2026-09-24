package com.xmut.ebus.domain.business.credit.model;

import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.support.CreditPeriodSupport;

import java.time.Instant;

/**
 * 积分账本账户聚合根（余额 / 预占占用 / 档位 / 月重置锚点）。
 * <p>
 * 可用额 = balance − reserved；预占时加 reserved，结算时 balance 与 reserved 同减，释放只减 reserved。
 */
public class CreditAccount {

    private String id;
    private String userId;
    private CreditTier tier;
    private int balance;
    private int reserved;
    private Instant periodAnchorAt;
    private Instant nextResetAt;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public static CreditAccount createFree(String id, String userId, Instant now) {
        CreditAccount account = new CreditAccount();
        account.id = id;
        account.userId = userId;
        account.tier = CreditTier.FREE;
        account.balance = CreditTier.FREE.getMonthlyQuota();
        account.reserved = 0;
        account.periodAnchorAt = now;
        account.nextResetAt = CreditPeriodSupport.firstResetAfter(now);
        account.version = 0;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public int available() {
        return balance - reserved;
    }

    public boolean needsMonthlyReset(Instant now) {
        return !now.isBefore(nextResetAt);
    }

    /**
     * 懒月重置：按当前档重发额度，上月剩余清零；活跃预占占用保留。
     */
    public void applyMonthlyReset(Instant now) {
        if (!needsMonthlyReset(now)) {
            return;
        }
        this.balance = tier.getMonthlyQuota();
        this.nextResetAt = CreditPeriodSupport.nextResetStrictlyAfter(periodAnchorAt, now);
        this.updatedAt = now;
    }

    /**
     * 手工升级：新档月额度、锚点=改档时刻并重算 nextResetAt；保留 reserved。
     *
     * @return {@code true} 已改档；{@code false} 同档幂等（无变更）
     * @throws IllegalArgumentException 降级或目标档无效
     */
    public boolean applyUpgrade(CreditTier targetTier, Instant now) {
        if (targetTier == null) {
            throw new IllegalArgumentException("target tier required");
        }
        if (now == null) {
            throw new IllegalArgumentException("upgrade time required");
        }
        if (this.tier == targetTier) {
            return false;
        }
        if (!targetTier.isStrictlyAbove(this.tier)) {
            throw new IllegalArgumentException("仅允许升级套餐，不能降级");
        }
        this.tier = targetTier;
        this.balance = targetTier.getMonthlyQuota();
        this.periodAnchorAt = now;
        this.nextResetAt = CreditPeriodSupport.firstResetAfter(now);
        this.updatedAt = now;
        return true;
    }

    public void applyReserve(int amount, Instant now) {
        this.reserved += amount;
        this.updatedAt = now;
    }

    public void applySettle(int amount, Instant now) {
        this.balance -= amount;
        this.reserved -= amount;
        this.updatedAt = now;
    }

    public void applyRelease(int amount, Instant now) {
        this.reserved -= amount;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public CreditTier getTier() {
        return tier;
    }

    public void setTier(CreditTier tier) {
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

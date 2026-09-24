package com.xmut.ebus.domain.business.credit.repository;

import com.xmut.ebus.domain.business.credit.model.CreditAccount;

import java.time.Instant;
import java.util.Optional;

/**
 * 积分账户持久化端口（CreditLedger 唯一写余额）。
 */
public interface CreditAccountRepository {

    void save(CreditAccount account);

    Optional<CreditAccount> findByUserId(String userId);

    Optional<CreditAccount> findById(String id);

    /**
     * 条件预占：available ≥ amount 且 version 匹配时 reserved+=amount、version++。
     *
     * @return 影响行数（0 表示不足或版本冲突）
     */
    int tryReserve(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * 结算：balance/reserved 同减，version++。
     */
    int trySettle(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * 释放预占：仅 reserved 减，version++。
     */
    int tryRelease(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * 月重置写回（含 version 条件）。
     */
    int tryApplyMonthlyReset(CreditAccount account, int expectedVersion);
}

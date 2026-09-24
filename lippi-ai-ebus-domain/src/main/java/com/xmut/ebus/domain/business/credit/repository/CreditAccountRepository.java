package com.xmut.ebus.domain.business.credit.repository;

import com.xmut.ebus.domain.business.credit.model.CreditAccount;

import java.time.Instant;
import java.util.Optional;

/**
 * 积分账户持久化端口（CreditLedger 唯一写余额）。
 * <p>
 * 条件更新返回影响行数：0 表示版本冲突或前置条件不满足。
 */
public interface CreditAccountRepository {

    void save(CreditAccount account);

    Optional<CreditAccount> findByUserId(String userId);

    Optional<CreditAccount> findById(String id);

    /**
     * reserved += amount（需 available ≥ amount 且 version 匹配）。
     */
    int updateAddReserved(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * balance/reserved 同减 amount（需二者均 ≥ amount 且 version 匹配）。
     */
    int updateSubtractBalanceAndReserved(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * reserved -= amount（需 reserved ≥ amount 且 version 匹配）。
     */
    int updateSubtractReserved(String accountId, int amount, int expectedVersion, Instant updatedAt);

    /**
     * 写回 balance、nextResetAt（version 匹配）。
     */
    int updateBalanceAndNextReset(CreditAccount account, int expectedVersion);

    /**
     * 写回 tier / balance / periodAnchorAt / nextResetAt（version 匹配；不改 reserved）。
     */
    int updateTierBalanceAndPeriod(CreditAccount account, int expectedVersion);
}

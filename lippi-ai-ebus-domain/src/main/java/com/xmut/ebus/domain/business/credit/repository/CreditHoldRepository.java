package com.xmut.ebus.domain.business.credit.repository;

import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.model.CreditHold;

import java.time.Instant;
import java.util.Optional;

/**
 * 积分预占持久化端口。
 */
public interface CreditHoldRepository {

    void save(CreditHold hold);

    Optional<CreditHold> findById(String id);

    /**
     * 条件完结：仅当 hold 仍为 ACTIVE 且归属 userId 时转入 newStatus。
     *
     * @return 影响行数（0 表示已被并发完结或归属不匹配）
     */
    int tryClaimFromActive(String holdId, String userId, CreditHoldStatus newStatus, Instant updatedAt);
}

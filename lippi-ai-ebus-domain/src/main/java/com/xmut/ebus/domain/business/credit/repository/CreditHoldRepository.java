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
     * 仅当 status=ACTIVE 且归属 userId 时更新 status。
     *
     * @return 影响行数（0 表示已被并发完结或归属不匹配）
     */
    int updateStatusIfActive(String holdId, String userId, CreditHoldStatus newStatus, Instant updatedAt);
}

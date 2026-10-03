package com.xmut.forma.domain.business.credit.repository;

import com.xmut.forma.domain.business.credit.model.CreditTierChange;

import java.util.List;

/**
 * 手工改档审计持久化端口。
 */
public interface CreditTierChangeRepository {

    void save(CreditTierChange change);

    List<CreditTierChange> findByTargetUserId(String targetUserId);
}

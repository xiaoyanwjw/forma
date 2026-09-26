package com.xmut.ebus.domain.business.picklist.repository;

import com.xmut.ebus.domain.business.picklist.model.Picklist;

import java.util.Optional;

/**
 * 选品清单仓储。
 */
public interface PicklistRepository {

    void save(Picklist picklist);

    Optional<Picklist> findById(String id);

    Optional<Picklist> findByRunId(String runId);
}

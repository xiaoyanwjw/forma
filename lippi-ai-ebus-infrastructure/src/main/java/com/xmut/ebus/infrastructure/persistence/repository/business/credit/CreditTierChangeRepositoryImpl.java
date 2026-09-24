package com.xmut.ebus.infrastructure.persistence.repository.business.credit;

import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditTierChange;
import com.xmut.ebus.domain.business.credit.repository.CreditTierChangeRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.CreditTierChangeMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.CreditTierChangePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CreditTierChangeRepositoryImpl implements CreditTierChangeRepository {

    private final CreditTierChangeMapper creditTierChangeMapper;

    @Override
    public void save(CreditTierChange change) {
        creditTierChangeMapper.insert(toPo(change));
    }

    @Override
    public List<CreditTierChange> findByTargetUserId(String targetUserId) {
        List<CreditTierChangePO> rows = creditTierChangeMapper.selectByTargetUserId(targetUserId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<CreditTierChange> result = new ArrayList<CreditTierChange>(rows.size());
        for (CreditTierChangePO po : rows) {
            result.add(toDomain(po));
        }
        return result;
    }

    private CreditTierChangePO toPo(CreditTierChange change) {
        CreditTierChangePO po = new CreditTierChangePO();
        po.setId(change.getId());
        po.setAccountId(change.getAccountId());
        po.setTargetUserId(change.getTargetUserId());
        po.setOperatorUserId(change.getOperatorUserId());
        po.setFromTier(change.getFromTier().name());
        po.setToTier(change.getToTier().name());
        po.setCreatedAt(change.getCreatedAt());
        return po;
    }

    private CreditTierChange toDomain(CreditTierChangePO po) {
        CreditTierChange change = new CreditTierChange();
        change.setId(po.getId());
        change.setAccountId(po.getAccountId());
        change.setTargetUserId(po.getTargetUserId());
        change.setOperatorUserId(po.getOperatorUserId());
        change.setFromTier(CreditTier.fromCode(po.getFromTier()));
        change.setToTier(CreditTier.fromCode(po.getToTier()));
        change.setCreatedAt(po.getCreatedAt());
        return change;
    }
}

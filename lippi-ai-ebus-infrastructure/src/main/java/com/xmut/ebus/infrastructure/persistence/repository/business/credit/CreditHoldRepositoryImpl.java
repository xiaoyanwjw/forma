package com.xmut.ebus.infrastructure.persistence.repository.business.credit;

import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.CreditHoldMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.CreditHoldPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CreditHoldRepositoryImpl implements CreditHoldRepository {

    private final CreditHoldMapper creditHoldMapper;

    @Override
    public void save(CreditHold hold) {
        creditHoldMapper.insert(toPo(hold));
    }

    @Override
    public Optional<CreditHold> findById(String id) {
        return Optional.ofNullable(creditHoldMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public int updateStatusIfActive(String holdId, String userId, CreditHoldStatus newStatus, Instant updatedAt) {
        return creditHoldMapper.updateStatusIfActive(holdId, userId, newStatus.name(), updatedAt);
    }

    private CreditHoldPO toPo(CreditHold hold) {
        CreditHoldPO po = new CreditHoldPO();
        po.setId(hold.getId());
        po.setAccountId(hold.getAccountId());
        po.setUserId(hold.getUserId());
        po.setAmount(hold.getAmount());
        po.setStatus(hold.getStatus().name());
        po.setCreatedAt(hold.getCreatedAt());
        po.setUpdatedAt(hold.getUpdatedAt());
        return po;
    }

    private CreditHold toDomain(CreditHoldPO po) {
        CreditHold hold = new CreditHold();
        hold.setId(po.getId());
        hold.setAccountId(po.getAccountId());
        hold.setUserId(po.getUserId());
        hold.setAmount(po.getAmount());
        hold.setStatus(CreditHoldStatus.fromCode(po.getStatus()));
        hold.setCreatedAt(po.getCreatedAt());
        hold.setUpdatedAt(po.getUpdatedAt());
        return hold;
    }
}

package com.xmut.forma.infrastructure.persistence.repository.business.credit;

import com.xmut.forma.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.forma.domain.business.credit.model.CreditHold;
import com.xmut.forma.domain.business.credit.repository.CreditHoldRepository;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.CreditHoldMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.CreditHoldPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Override
    public List<CreditHold> listSettledByUserId(String userId, int limit) {
        List<CreditHoldPO> rows = creditHoldMapper.listSettledByUserId(userId, limit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private CreditHoldPO toPo(CreditHold hold) {
        CreditHoldPO po = new CreditHoldPO();
        po.setBizId(hold.getId());
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
        hold.setId(po.getBizId());
        hold.setAccountId(po.getAccountId());
        hold.setUserId(po.getUserId());
        hold.setAmount(po.getAmount());
        hold.setStatus(CreditHoldStatus.fromCode(po.getStatus()));
        hold.setCreatedAt(po.getCreatedAt());
        hold.setUpdatedAt(po.getUpdatedAt());
        return hold;
    }
}

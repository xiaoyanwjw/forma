package com.xmut.ebus.infrastructure.persistence.repository.business.credit;

import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.CreditAccountMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.CreditAccountPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CreditAccountRepositoryImpl implements CreditAccountRepository {

    private final CreditAccountMapper creditAccountMapper;

    @Override
    public void save(CreditAccount account) {
        creditAccountMapper.insert(toPo(account));
    }

    @Override
    public Optional<CreditAccount> findByUserId(String userId) {
        return Optional.ofNullable(creditAccountMapper.selectByUserId(userId)).map(this::toDomain);
    }

    @Override
    public Optional<CreditAccount> findById(String id) {
        return Optional.ofNullable(creditAccountMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public int tryReserve(String accountId, int amount, int expectedVersion, Instant updatedAt) {
        return creditAccountMapper.tryReserve(accountId, amount, expectedVersion, updatedAt);
    }

    @Override
    public int trySettle(String accountId, int amount, int expectedVersion, Instant updatedAt) {
        return creditAccountMapper.trySettle(accountId, amount, expectedVersion, updatedAt);
    }

    @Override
    public int tryRelease(String accountId, int amount, int expectedVersion, Instant updatedAt) {
        return creditAccountMapper.tryRelease(accountId, amount, expectedVersion, updatedAt);
    }

    @Override
    public int tryApplyMonthlyReset(CreditAccount account, int expectedVersion) {
        return creditAccountMapper.tryApplyMonthlyReset(
                account.getId(),
                account.getBalance(),
                account.getNextResetAt(),
                expectedVersion,
                account.getUpdatedAt());
    }

    private CreditAccountPO toPo(CreditAccount account) {
        CreditAccountPO po = new CreditAccountPO();
        po.setId(account.getId());
        po.setUserId(account.getUserId());
        po.setTier(account.getTier().name());
        po.setBalance(account.getBalance());
        po.setReserved(account.getReserved());
        po.setPeriodAnchorAt(account.getPeriodAnchorAt());
        po.setNextResetAt(account.getNextResetAt());
        po.setVersion(account.getVersion());
        po.setCreatedAt(account.getCreatedAt());
        po.setUpdatedAt(account.getUpdatedAt());
        return po;
    }

    private CreditAccount toDomain(CreditAccountPO po) {
        CreditAccount account = new CreditAccount();
        account.setId(po.getId());
        account.setUserId(po.getUserId());
        account.setTier(CreditTier.fromCode(po.getTier()));
        account.setBalance(po.getBalance());
        account.setReserved(po.getReserved());
        account.setPeriodAnchorAt(po.getPeriodAnchorAt());
        account.setNextResetAt(po.getNextResetAt());
        account.setVersion(po.getVersion());
        account.setCreatedAt(po.getCreatedAt());
        account.setUpdatedAt(po.getUpdatedAt());
        return account;
    }
}

package com.xmut.ebus.application.business.credit.service;

import com.xmut.ebus.common.cas.CasConflictException;
import com.xmut.ebus.common.cas.CasRetry;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.model.CreditTierChange;
import com.xmut.ebus.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import com.xmut.ebus.domain.business.credit.repository.CreditTierChangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * CreditLedger 单次 CAS 尝试（由 {@link CasRetry} 重试）。settle/release 整段编排不放在本类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCasWriter {

    private static final int HOLD_AMOUNT = 1;

    private final CreditAccountRepository creditAccountRepository;
    private final CreditHoldRepository creditHoldRepository;
    private final CreditTierChangeRepository creditTierChangeRepository;
    private final Clock clock;
    private final ObjectProvider<CreditApplicationService> creditApplicationService;

    /**
     * 预占 1 点：ensureReady → 条件加 reserved → 建 hold。
     */
    @CasRetry(exhaustedMessage = "积分预占冲突，请稍后重试")
    public String reserveAttempt(String userId) {
        CreditAccount account = creditApplicationService.getObject().ensureReady(userId);
        if (account.available() < HOLD_AMOUNT) {
            LoggerUtils.warn(log, CreditCasWriter.class, "reserveAttempt",
                    "insufficient credits",
                    NameValue.create("userId", userId),
                    NameValue.create("available", account.available()));
            throw new BusinessException(ErrorCode.CREDIT_INSUFFICIENT);
        }
        Instant now = Instant.now(clock);
        int updated = creditAccountRepository.updateAddReserved(
                account.getId(), HOLD_AMOUNT, account.getVersion(), now);
        if (updated == 0) {
            throw new CasConflictException();
        }
        String holdId = UUID.randomUUID().toString();
        creditHoldRepository.save(CreditHold.createActive(holdId, account.getId(), userId, HOLD_AMOUNT, now));
        LoggerUtils.success(log, CreditCasWriter.class, "reserveAttempt",
                NameValue.create("userId", userId),
                NameValue.create("holdId", holdId),
                NameValue.create("accountId", account.getId()));
        return holdId;
    }

    /**
     * 已 claim 的 hold：条件扣 balance 与 reserved。
     */
    @CasRetry(exhaustedMessage = "积分结算冲突，请稍后重试")
    public void settleAccountAttempt(CreditHold hold, Instant now) {
        CreditAccount account = creditAccountRepository.findById(hold.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
        int updated = creditAccountRepository.updateSubtractBalanceAndReserved(
                account.getId(), hold.getAmount(), account.getVersion(), now);
        if (updated == 0) {
            throw new CasConflictException();
        }
        LoggerUtils.success(log, CreditCasWriter.class, "settleAccountAttempt",
                NameValue.create("userId", hold.getUserId()),
                NameValue.create("holdId", hold.getId()));
    }

    /**
     * 已 claim 的 hold：条件减 reserved。
     */
    @CasRetry(exhaustedMessage = "积分释放冲突，请稍后重试")
    public void releaseAccountAttempt(CreditHold hold, Instant now) {
        CreditAccount account = creditAccountRepository.findById(hold.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
        int updated = creditAccountRepository.updateSubtractReserved(
                account.getId(), hold.getAmount(), account.getVersion(), now);
        if (updated == 0) {
            throw new CasConflictException();
        }
        LoggerUtils.success(log, CreditCasWriter.class, "releaseAccountAttempt",
                NameValue.create("userId", hold.getUserId()),
                NameValue.create("holdId", hold.getId()));
    }

    /**
     * 懒月重置：无需重置则原样返回；冲突抛 {@link CasConflictException}。
     */
    @CasRetry(exhaustedMessage = "月重置冲突，请稍后重试")
    public CreditAccount monthlyResetAttempt(String accountId, Instant now) {
        CreditAccount current = creditAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
        if (!current.needsMonthlyReset(now)) {
            return current;
        }
        int expectedVersion = current.getVersion();
        current.applyMonthlyReset(now);
        int updated = creditAccountRepository.updateBalanceAndNextReset(current, expectedVersion);
        if (updated == 0) {
            throw new CasConflictException();
        }
        current.setVersion(expectedVersion + 1);
        LoggerUtils.success(log, CreditCasWriter.class, "monthlyResetAttempt",
                NameValue.create("userId", current.getUserId()),
                NameValue.create("accountId", current.getId()),
                NameValue.create("nextResetAt", current.getNextResetAt()));
        return current;
    }

    /**
     * 手工改档：CAS 写 tier/balance/锚点后同事务落审计；同档幂等不写审计。
     */
    @CasRetry(exhaustedMessage = "改档冲突，请稍后重试")
    public CreditAccount changeTierAttempt(String targetUserId,
                                           CreditTier targetTier,
                                           String operatorUserId,
                                           Instant now) {
        CreditAccount account = creditApplicationService.getObject().ensureReady(targetUserId);
        if (account.getTier() == targetTier) {
            return account;
        }
        if (!targetTier.isStrictlyAbove(account.getTier())) {
            throw new BusinessException(ErrorCode.CREDIT_TIER_INVALID, "仅允许升级套餐，不能降级");
        }
        CreditTier fromTier = account.getTier();
        int expectedVersion = account.getVersion();
        boolean changed = account.applyUpgrade(targetTier, now);
        if (!changed) {
            return account;
        }
        int updated = creditAccountRepository.updateTierBalanceAndPeriod(account, expectedVersion);
        if (updated == 0) {
            throw new CasConflictException();
        }
        creditTierChangeRepository.save(CreditTierChange.create(
                UUID.randomUUID().toString(),
                account.getId(),
                targetUserId,
                operatorUserId,
                fromTier,
                targetTier,
                now));
        account.setVersion(expectedVersion + 1);
        LoggerUtils.success(log, CreditCasWriter.class, "changeTierAttempt",
                NameValue.create("targetUserId", targetUserId),
                NameValue.create("operatorUserId", operatorUserId),
                NameValue.create("fromTier", fromTier.name()),
                NameValue.create("toTier", targetTier.name()));
        return account;
    }
}

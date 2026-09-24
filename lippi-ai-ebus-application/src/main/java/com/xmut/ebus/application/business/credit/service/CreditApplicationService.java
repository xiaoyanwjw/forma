package com.xmut.ebus.application.business.credit.service;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * CreditLedger 写用例：建账 / 预占 / 结算 / 释放 / 懒月重置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditApplicationService {

    private static final int HOLD_AMOUNT = 1;
    private static final int MAX_CAS_RETRIES = 8;

    private final CreditAccountRepository creditAccountRepository;
    private final CreditHoldRepository creditHoldRepository;
    private final Clock clock;

    /**
     * 注册成功后同事务建免费账本（锚点=注册时刻）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initFreeAccount(String userId, Instant periodAnchor) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户 ID 不能为空");
        }
        if (creditAccountRepository.findByUserId(userId).isPresent()) {
            return;
        }
        Instant anchor = periodAnchor != null ? periodAnchor : Instant.now(clock);
        CreditAccount account = CreditAccount.createFree(UUID.randomUUID().toString(), userId, anchor);
        try {
            creditAccountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            // 并发补偿建账：已存在则忽略
            log.info("credit account already exists userId={}", userId);
            return;
        }
        log.info("credit free account created userId={} accountId={}", userId, account.getId());
    }

    /**
     * 查询/预占前：无账本则补偿建免费账；过重置点则懒月重置。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreditAccount ensureReady(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户 ID 不能为空");
        }
        Instant now = Instant.now(clock);
        CreditAccount account = creditAccountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            initFreeAccount(userId, now);
            account = creditAccountRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本初始化失败"));
        }
        return applyLazyMonthlyReset(account, now);
    }

    /**
     * 预占 1 点：返回 holdId。
     */
    @Transactional(rollbackFor = Exception.class)
    public String reserveOne(String userId) {
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            CreditAccount account = ensureReady(userId);
            if (account.available() < HOLD_AMOUNT) {
                throw new BusinessException(ErrorCode.CREDIT_INSUFFICIENT);
            }
            Instant now = Instant.now(clock);
            int expectedVersion = account.getVersion();
            int updated = creditAccountRepository.tryReserve(account.getId(), HOLD_AMOUNT, expectedVersion, now);
            if (updated == 0) {
                continue;
            }
            String holdId = UUID.randomUUID().toString();
            CreditHold hold = CreditHold.createActive(holdId, account.getId(), userId, HOLD_AMOUNT, now);
            creditHoldRepository.save(hold);
            log.info("credit reserved userId={} holdId={} accountId={}", userId, holdId, account.getId());
            return holdId;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "积分预占冲突，请稍后重试");
    }

    /**
     * 结算：调用方须已保证可用成果落库。先 CAS 完结 hold，再扣账户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void settle(String userId, String holdId) {
        CreditHold hold = requireHoldOwnedBy(userId, holdId);
        Instant now = Instant.now(clock);
        int claimed = creditHoldRepository.tryClaimFromActive(
                holdId, userId, CreditHoldStatus.SETTLED, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        hold.markSettled(now);
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            CreditAccount account = creditAccountRepository.findById(hold.getAccountId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
            int updated = creditAccountRepository.trySettle(
                    account.getId(), hold.getAmount(), account.getVersion(), now);
            if (updated == 0) {
                continue;
            }
            log.info("credit settled userId={} holdId={}", userId, holdId);
            return;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "积分结算冲突，请稍后重试");
    }

    /**
     * 释放预占：先 CAS 完结 hold，再解冻账户占用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(String userId, String holdId) {
        CreditHold hold = requireHoldOwnedBy(userId, holdId);
        Instant now = Instant.now(clock);
        int claimed = creditHoldRepository.tryClaimFromActive(
                holdId, userId, CreditHoldStatus.RELEASED, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        hold.markReleased(now);
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            CreditAccount account = creditAccountRepository.findById(hold.getAccountId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
            int updated = creditAccountRepository.tryRelease(
                    account.getId(), hold.getAmount(), account.getVersion(), now);
            if (updated == 0) {
                continue;
            }
            log.info("credit released userId={} holdId={}", userId, holdId);
            return;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "积分释放冲突，请稍后重试");
    }

    private CreditAccount applyLazyMonthlyReset(CreditAccount account, Instant now) {
        if (!account.needsMonthlyReset(now)) {
            return account;
        }
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            CreditAccount current = creditAccountRepository.findById(account.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "积分账本不存在"));
            if (!current.needsMonthlyReset(now)) {
                return current;
            }
            int expectedVersion = current.getVersion();
            current.applyMonthlyReset(now);
            int updated = creditAccountRepository.tryApplyMonthlyReset(current, expectedVersion);
            if (updated > 0) {
                current.setVersion(expectedVersion + 1);
                log.info("credit monthly reset userId={} accountId={} nextResetAt={}",
                        current.getUserId(), current.getId(), current.getNextResetAt());
                return current;
            }
            account = current;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "月重置冲突，请稍后重试");
    }

    private CreditHold requireHoldOwnedBy(String userId, String holdId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户 ID 不能为空");
        }
        if (!StringUtils.hasText(holdId)) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        CreditHold hold = creditHoldRepository.findById(holdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_HOLD_INVALID));
        if (!userId.equals(hold.getUserId()) || hold.getStatus() != CreditHoldStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        return hold;
    }
}

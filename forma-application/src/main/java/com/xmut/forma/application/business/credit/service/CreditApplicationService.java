package com.xmut.forma.application.business.credit.service;

import com.xmut.forma.application.business.credit.command.ChangeTierCommand;
import com.xmut.forma.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.forma.application.business.credit.support.CreditAdminAuthorization;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.common.util.ObjectUtils;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.forma.domain.business.credit.constant.CreditTier;
import com.xmut.forma.domain.business.credit.model.CreditAccount;
import com.xmut.forma.domain.business.credit.model.CreditHold;
import com.xmut.forma.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.forma.domain.business.credit.repository.CreditHoldRepository;
import com.xmut.forma.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * CreditLedger 写用例：建账 / 预占 / 结算 / 释放 / 懒月重置 / 手工改档。
 * <p>
 * CAS 重试在 {@link CreditCasWriter}；settle/release 先 claim hold 一次，再调账户尝试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditApplicationService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditHoldRepository creditHoldRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final CreditCasWriter creditCasWriter;
    private final CreditAdminAuthorization creditAdminAuthorization;

    /**
     * 注册成功后同事务建免费账本（锚点=注册时刻）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initFreeAccount(String userId, Instant periodAnchor) {
        StringUtils.requireHasText(userId, "用户 ID 不能为空");
        if (creditAccountRepository.findByUserId(userId).isPresent()) {
            return;
        }
        Instant anchor = periodAnchor != null ? periodAnchor : Instant.now(clock);
        CreditAccount account = CreditAccount.createFree(UUID.randomUUID().toString(), userId, anchor);
        try {
            creditAccountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            // 并发补偿建账：已存在则忽略
            LoggerUtils.warn(log, CreditApplicationService.class, "initFreeAccount",
                    "credit account already exists",
                    NameValue.create("userId", userId));
            return;
        }
        LoggerUtils.success(log, CreditApplicationService.class, "initFreeAccount",
                NameValue.create("userId", userId),
                NameValue.create("accountId", account.getId()));
    }

    /**
     * 查询/预占前：无账本则补偿建免费账；过重置点则懒月重置。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreditAccount ensureReady(String userId) {
        StringUtils.requireHasText(userId, "用户 ID 不能为空");
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
     * 手工改档升级（白名单操作者）：目标无账本则先 ensureReady；同档幂等不写审计。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreditBalanceDTO changeTier(ChangeTierCommand command) {
        ObjectUtils.requireNonNull(command, "改档命令不能为空");
        creditAdminAuthorization.requireOperatorAllowed(command.getUserId());
        String targetUserId = StringUtils.requireHasText(command.getTargetUserId(), "目标用户 ID 不能为空");
        CreditTier targetTier = ObjectUtils.requireNonNull(command.getTargetTier(), "目标套餐不能为空");
        if (!userRepository.findById(targetUserId).isPresent()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标用户不存在");
        }

        Instant now = Instant.now(clock);
        CreditAccount account = ensureReady(targetUserId);
        if (account.getTier() == targetTier) {
            LoggerUtils.success(log, CreditApplicationService.class, "changeTier",
                    NameValue.create("targetUserId", targetUserId),
                    NameValue.create("tier", targetTier.name()),
                    NameValue.create("idempotent", true));
            return toBalanceDto(account);
        }
        if (!targetTier.isStrictlyAbove(account.getTier())) {
            throw new BusinessException(ErrorCode.CREDIT_TIER_INVALID, "仅允许升级套餐，不能降级");
        }

        account = creditCasWriter.changeTierAttempt(
                targetUserId, targetTier, command.getUserId(), now);
        LoggerUtils.success(log, CreditApplicationService.class, "changeTier",
                NameValue.create("operatorUserId", command.getUserId()),
                NameValue.create("targetUserId", targetUserId),
                NameValue.create("toTier", targetTier.name()),
                NameValue.create("balance", account.getBalance()));
        return toBalanceDto(account);
    }

    /**
     * 预占 1 点：返回 holdId。
     */
    @Transactional(rollbackFor = Exception.class)
    public String reserveOne(String userId) {
        return creditCasWriter.reserveAttempt(userId);
    }

    /**
     * 结算：调用方须已保证可用成果落库。先 CAS 完结 hold，再扣账户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void settle(String userId, String holdId) {
        CreditHold hold = requireHoldOwnedBy(userId, holdId);
        Instant now = Instant.now(clock);
        int claimed = creditHoldRepository.updateStatusIfActive(
                holdId, userId, CreditHoldStatus.SETTLED, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        hold.markSettled(now);
        creditCasWriter.settleAccountAttempt(hold, now);
    }

    /**
     * 释放预占：先 CAS 完结 hold，再解冻账户占用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(String userId, String holdId) {
        CreditHold hold = requireHoldOwnedBy(userId, holdId);
        Instant now = Instant.now(clock);
        int claimed = creditHoldRepository.updateStatusIfActive(
                holdId, userId, CreditHoldStatus.RELEASED, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        hold.markReleased(now);
        creditCasWriter.releaseAccountAttempt(hold, now);
    }

    private CreditAccount applyLazyMonthlyReset(CreditAccount account, Instant now) {
        if (!account.needsMonthlyReset(now)) {
            return account;
        }
        return creditCasWriter.monthlyResetAttempt(account.getId(), now);
    }

    private CreditHold requireHoldOwnedBy(String userId, String holdId) {
        StringUtils.requireHasText(userId, "用户 ID 不能为空");
        String normalizedHoldId = StringUtils.requireHasText(holdId, ErrorCode.CREDIT_HOLD_INVALID);
        CreditHold hold = creditHoldRepository.findById(normalizedHoldId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_HOLD_INVALID));
        if (!userId.equals(hold.getUserId()) || hold.getStatus() != CreditHoldStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CREDIT_HOLD_INVALID);
        }
        return hold;
    }

    private static CreditBalanceDTO toBalanceDto(CreditAccount account) {
        return new CreditBalanceDTO(
                account.getTier().name(),
                account.available(),
                account.getBalance(),
                account.getReserved(),
                account.getNextResetAt(),
                account.getPeriodAnchorAt());
    }
}

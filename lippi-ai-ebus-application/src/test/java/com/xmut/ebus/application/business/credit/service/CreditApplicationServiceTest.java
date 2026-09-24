package com.xmut.ebus.application.business.credit.service;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T08:00:00Z");
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private CreditAccountRepository creditAccountRepository;
    @Mock
    private CreditHoldRepository creditHoldRepository;
    @Mock
    private CreditCasWriter creditCasWriter;

    private CreditApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new CreditApplicationService(
                creditAccountRepository, creditHoldRepository, clock, creditCasWriter);
    }

    @Test
    void initFreeAccountCreatesQuota20() {
        when(creditAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.initFreeAccount(USER_ID, NOW);

        ArgumentCaptor<CreditAccount> captor = ArgumentCaptor.forClass(CreditAccount.class);
        verify(creditAccountRepository).save(captor.capture());
        CreditAccount saved = captor.getValue();
        assertEquals(CreditTier.FREE, saved.getTier());
        assertEquals(20, saved.getBalance());
        assertEquals(0, saved.getReserved());
        assertEquals(NOW, saved.getPeriodAnchorAt());
        assertEquals(Instant.parse("2026-04-15T08:00:00Z"), saved.getNextResetAt());
    }

    @Test
    void reserveRejectsWhenAvailableZero() {
        when(creditCasWriter.reserveAttempt(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.CREDIT_INSUFFICIENT));

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.reserveOne(USER_ID);
            }
        });
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        assertEquals("积分不足", ex.getMessage());
        verify(creditCasWriter).reserveAttempt(USER_ID);
        verify(creditHoldRepository, never()).save(any(CreditHold.class));
    }

    @Test
    void reserveCreatesHoldWhenAvailable() {
        when(creditCasWriter.reserveAttempt(USER_ID)).thenReturn("hold-created");

        String holdId = service.reserveOne(USER_ID);

        assertEquals("hold-created", holdId);
        verify(creditCasWriter).reserveAttempt(USER_ID);
    }

    @Test
    void reserveCasExhaustionThrowsSystemError() {
        when(creditCasWriter.reserveAttempt(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "积分预占冲突，请稍后重试"));

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.reserveOne(USER_ID);
            }
        });
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        assertEquals("积分预占冲突，请稍后重试", ex.getMessage());
        verify(creditHoldRepository, never()).save(any(CreditHold.class));
    }

    @Test
    void settleClaimsHoldBeforeDebit() {
        CreditHold hold = CreditHold.createActive("hold-1", ACCOUNT_ID, USER_ID, 1, NOW);
        when(creditHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(creditHoldRepository.updateStatusIfActive(
                eq("hold-1"), eq(USER_ID), eq(CreditHoldStatus.SETTLED), eq(NOW))).thenReturn(1);

        service.settle(USER_ID, "hold-1");

        verify(creditHoldRepository).updateStatusIfActive(
                eq("hold-1"), eq(USER_ID), eq(CreditHoldStatus.SETTLED), eq(NOW));
        verify(creditCasWriter).settleAccountAttempt(hold, NOW);
    }

    @Test
    void releaseClaimsHoldBeforeUnfreeze() {
        CreditHold hold = CreditHold.createActive("hold-1", ACCOUNT_ID, USER_ID, 1, NOW);
        when(creditHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(creditHoldRepository.updateStatusIfActive(
                eq("hold-1"), eq(USER_ID), eq(CreditHoldStatus.RELEASED), eq(NOW))).thenReturn(1);

        service.release(USER_ID, "hold-1");

        verify(creditHoldRepository).updateStatusIfActive(
                eq("hold-1"), eq(USER_ID), eq(CreditHoldStatus.RELEASED), eq(NOW));
        verify(creditCasWriter).releaseAccountAttempt(hold, NOW);
        verify(creditCasWriter, never()).settleAccountAttempt(any(CreditHold.class), any(Instant.class));
    }

    @Test
    void settleInvalidHoldFails() {
        when(creditHoldRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.settle(USER_ID, "missing");
            }
        });
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, ex.getErrorCode());
    }

    @Test
    void settleBlankUserIdParamInvalid() {
        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.settle("  ", "hold-1");
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(creditHoldRepository, never()).findById(anyString());
    }

    @Test
    void settleNullUserIdParamInvalid() {
        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.settle(null, "hold-1");
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void settleClaimRaceReturnsHoldInvalid() {
        CreditHold hold = CreditHold.createActive("hold-1", ACCOUNT_ID, USER_ID, 1, NOW);
        when(creditHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(creditHoldRepository.updateStatusIfActive(
                eq("hold-1"), eq(USER_ID), eq(CreditHoldStatus.SETTLED), eq(NOW))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.settle(USER_ID, "hold-1");
            }
        });
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, ex.getErrorCode());
        verify(creditCasWriter, never()).settleAccountAttempt(any(CreditHold.class), any(Instant.class));
    }

    @Test
    void ensureReadyAppliesMonthlyReset() {
        CreditAccount account = freeAccount(3, 0);
        account.setPeriodAnchorAt(Instant.parse("2026-01-15T08:00:00Z"));
        account.setNextResetAt(Instant.parse("2026-02-15T08:00:00Z"));
        CreditAccount reset = freeAccount(20, 0);
        reset.setNextResetAt(Instant.parse("2026-04-15T08:00:00Z"));
        when(creditAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(creditCasWriter.monthlyResetAttempt(ACCOUNT_ID, NOW)).thenReturn(reset);

        CreditAccount ready = service.ensureReady(USER_ID);

        assertEquals(20, ready.getBalance());
        assertEquals(Instant.parse("2026-04-15T08:00:00Z"), ready.getNextResetAt());
        verify(creditCasWriter).monthlyResetAttempt(ACCOUNT_ID, NOW);
    }

    private static CreditAccount freeAccount(int balance, int reserved) {
        CreditAccount account = new CreditAccount();
        account.setId(ACCOUNT_ID);
        account.setUserId(USER_ID);
        account.setTier(CreditTier.FREE);
        account.setBalance(balance);
        account.setReserved(reserved);
        account.setPeriodAnchorAt(NOW);
        account.setNextResetAt(Instant.parse("2026-04-15T08:00:00Z"));
        account.setVersion(0);
        account.setCreatedAt(NOW);
        account.setUpdatedAt(NOW);
        return account;
    }
}

package com.xmut.ebus.application.business.credit.service;

import com.xmut.ebus.common.cas.CasConflictException;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.repository.CreditAccountRepository;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCasWriterTest {

    private static final Instant NOW = Instant.parse("2026-03-15T08:00:00Z");
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private CreditAccountRepository creditAccountRepository;
    @Mock
    private CreditHoldRepository creditHoldRepository;
    @Mock
    private ObjectProvider<CreditApplicationService> creditApplicationServiceProvider;
    @Mock
    private CreditApplicationService creditApplicationService;

    private CreditCasWriter casWriter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        casWriter = new CreditCasWriter(
                creditAccountRepository, creditHoldRepository, clock, creditApplicationServiceProvider);
    }

    @Test
    void reserveAttemptThrowsCasConflictWhenUpdateMisses() {
        CreditAccount account = freeAccount(20, 0);
        when(creditApplicationServiceProvider.getObject()).thenReturn(creditApplicationService);
        when(creditApplicationService.ensureReady(USER_ID)).thenReturn(account);
        when(creditAccountRepository.updateAddReserved(eq(ACCOUNT_ID), eq(1), eq(0), eq(NOW))).thenReturn(0);

        assertThrows(CasConflictException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                casWriter.reserveAttempt(USER_ID);
            }
        });
        verify(creditHoldRepository, never()).save(any(CreditHold.class));
    }

    @Test
    void reserveAttemptInsufficientDoesNotThrowCasConflict() {
        CreditAccount account = freeAccount(0, 0);
        when(creditApplicationServiceProvider.getObject()).thenReturn(creditApplicationService);
        when(creditApplicationService.ensureReady(USER_ID)).thenReturn(account);

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                casWriter.reserveAttempt(USER_ID);
            }
        });
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        verify(creditAccountRepository, never()).updateAddReserved(anyString(), anyInt(), anyInt(), any(Instant.class));
        verify(creditHoldRepository, never()).save(any(CreditHold.class));
    }

    @Test
    void settleAccountAttemptDebitsOnSuccess() {
        CreditHold hold = CreditHold.createActive("hold-1", ACCOUNT_ID, USER_ID, 1, NOW);
        CreditAccount account = freeAccount(20, 1);
        account.setVersion(3);
        when(creditAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(creditAccountRepository.updateSubtractBalanceAndReserved(eq(ACCOUNT_ID), eq(1), eq(3), eq(NOW)))
                .thenReturn(1);

        casWriter.settleAccountAttempt(hold, NOW);

        verify(creditAccountRepository).updateSubtractBalanceAndReserved(eq(ACCOUNT_ID), eq(1), eq(3), eq(NOW));
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

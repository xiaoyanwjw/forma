package com.xmut.ebus.application.business.credit.query;

import com.xmut.ebus.application.business.credit.dto.CreditUsageDTO;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import com.xmut.ebus.domain.business.credit.model.CreditHold;
import com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditQueryServiceTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final Instant NEXT_RESET = Instant.parse("2026-10-24T10:00:00Z");
    private static final Instant ANCHOR = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private CreditApplicationService creditApplicationService;
    @Mock
    private CreditHoldRepository creditHoldRepository;

    private CreditQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new CreditQueryService(creditApplicationService, creditHoldRepository);
    }

    @Test
    void findUsageReturnsSummaryAndSettledEntriesOnly() {
        CreditAccount account = CreditAccount.createFree("acc-1", USER_ID, ANCHOR);
        account.setBalance(18);
        account.setReserved(0);
        account.setNextResetAt(NEXT_RESET);
        when(creditApplicationService.ensureReady(USER_ID)).thenReturn(account);

        CreditHold settled = CreditHold.createActive("hold-s", "acc-1", USER_ID, 1, ANCHOR);
        settled.markSettled(Instant.parse("2026-09-25T12:00:00Z"));
        when(creditHoldRepository.listSettledByUserId(eq(USER_ID), eq(CreditQueryService.USAGE_ENTRY_LIMIT)))
                .thenReturn(Collections.singletonList(settled));

        CreditUsageDTO usage = queryService.findUsage(USER_ID);

        assertEquals(CreditTier.FREE.name(), usage.getTier());
        assertEquals(18, usage.getAvailable());
        assertEquals(20, usage.getMonthlyQuota());
        assertEquals(2, usage.getUsed());
        assertEquals(NEXT_RESET, usage.getNextResetAt());
        assertEquals(1, usage.getEntries().size());
        CreditUsageDTO.CreditUsageEntryDTO entry = usage.getEntries().get(0);
        assertEquals("hold-s", entry.getHoldId());
        assertEquals(CreditUsageDTO.ENTRY_TITLE_SETTLED, entry.getTitle());
        assertEquals(1, entry.getAmount());
        assertEquals(-1, entry.getDelta());
        assertEquals(Instant.parse("2026-09-25T12:00:00Z"), entry.getOccurredAt());
        verify(creditHoldRepository).listSettledByUserId(USER_ID, CreditQueryService.USAGE_ENTRY_LIMIT);
    }

    @Test
    void findUsageEmptyEntriesWhenNoSettledHolds() {
        CreditAccount account = CreditAccount.createFree("acc-1", USER_ID, ANCHOR);
        account.setNextResetAt(NEXT_RESET);
        when(creditApplicationService.ensureReady(USER_ID)).thenReturn(account);
        when(creditHoldRepository.listSettledByUserId(eq(USER_ID), eq(CreditQueryService.USAGE_ENTRY_LIMIT)))
                .thenReturn(Collections.emptyList());

        CreditUsageDTO usage = queryService.findUsage(USER_ID);

        assertEquals(20, usage.getAvailable());
        assertEquals(0, usage.getUsed());
        assertTrue(usage.getEntries().isEmpty());
    }

    @Test
    void findUsageDoesNotSurfaceActiveHoldsInEntries() {
        CreditAccount account = CreditAccount.createFree("acc-1", USER_ID, ANCHOR);
        account.setBalance(20);
        account.setReserved(1);
        account.setNextResetAt(NEXT_RESET);
        when(creditApplicationService.ensureReady(USER_ID)).thenReturn(account);
        // repository contract: only SETTLED returned
        when(creditHoldRepository.listSettledByUserId(eq(USER_ID), eq(CreditQueryService.USAGE_ENTRY_LIMIT)))
                .thenReturn(Collections.emptyList());

        CreditUsageDTO usage = queryService.findUsage(USER_ID);

        assertEquals(19, usage.getAvailable());
        assertEquals(1, usage.getUsed());
        assertTrue(usage.getEntries().isEmpty());
        List<CreditHold> neverPassed = Arrays.asList(
                CreditHold.createActive("active-1", "acc-1", USER_ID, 1, ANCHOR));
        assertEquals(CreditHoldStatus.ACTIVE, neverPassed.get(0).getStatus());
    }
}

package com.xmut.forma.domain.business.credit.model;

import com.xmut.forma.domain.business.credit.constant.CreditTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditAccountUpgradeTest {

    private static final Instant NOW = Instant.parse("2026-03-15T08:00:00Z");

    @Test
    void applyUpgradeFreeToProResetsAnchorAndQuotaKeepsReserved() {
        CreditAccount account = CreditAccount.createFree("a1", "u1", Instant.parse("2026-01-01T00:00:00Z"));
        account.setBalance(3);
        account.setReserved(2);

        assertTrue(account.applyUpgrade(CreditTier.PRO, NOW));

        assertEquals(CreditTier.PRO, account.getTier());
        assertEquals(200, account.getBalance());
        assertEquals(2, account.getReserved());
        assertEquals(198, account.available());
        assertEquals(NOW, account.getPeriodAnchorAt());
        assertEquals(Instant.parse("2026-04-15T08:00:00Z"), account.getNextResetAt());
    }

    @Test
    void applyUpgradeSameTierIdempotent() {
        CreditAccount account = CreditAccount.createFree("a1", "u1", NOW);
        account.setTier(CreditTier.PRO);
        account.setBalance(200);
        Instant anchor = account.getPeriodAnchorAt();

        assertFalse(account.applyUpgrade(CreditTier.PRO, Instant.parse("2026-06-01T00:00:00Z")));

        assertEquals(CreditTier.PRO, account.getTier());
        assertEquals(200, account.getBalance());
        assertEquals(anchor, account.getPeriodAnchorAt());
    }

    @Test
    void applyUpgradeRejectsDowngrade() {
        CreditAccount account = CreditAccount.createFree("a1", "u1", NOW);
        account.setTier(CreditTier.PRO);
        account.setBalance(200);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        account.applyUpgrade(CreditTier.FREE, NOW);
                    }
                });
        assertTrue(ex.getMessage().contains("降级"));
        assertEquals(CreditTier.PRO, account.getTier());
        assertEquals(200, account.getBalance());
    }

    @Test
    void applyUpgradeWhenReservedExceedsNewQuotaAvailableNegative() {
        CreditAccount account = CreditAccount.createFree("a1", "u1", NOW);
        account.setReserved(250);

        assertTrue(account.applyUpgrade(CreditTier.PRO, NOW));

        assertEquals(200, account.getBalance());
        assertEquals(250, account.getReserved());
        assertEquals(-50, account.available());
    }
}

package com.xmut.forma.domain.business.credit.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditPeriodSupportTest {

    @Test
    void firstResetIsOneCalendarMonthAfterAnchor() {
        Instant anchor = Instant.parse("2026-01-15T10:00:00Z");
        Instant next = CreditPeriodSupport.firstResetAfter(anchor);
        assertEquals(Instant.parse("2026-02-15T10:00:00Z"), next);
    }

    @Test
    void january31ClampsToFebruaryEnd() {
        Instant anchor = Instant.parse("2026-01-31T12:00:00Z");
        Instant next = CreditPeriodSupport.firstResetAfter(anchor);
        ZonedDateTime zdt = next.atZone(ZoneOffset.UTC);
        assertEquals(2, zdt.getMonthValue());
        assertEquals(28, zdt.getDayOfMonth());
    }

    @Test
    void catchUpSkipsMissedMonthsFromOriginalAnchorDay() {
        Instant anchor = Instant.parse("2026-01-31T10:00:00Z");
        Instant now = Instant.parse("2026-03-20T10:00:00Z");
        Instant next = CreditPeriodSupport.nextResetStrictlyAfter(anchor, now);
        // Jan31+2m = Mar31 (Java plusMonths from Jan31: Feb28, Mar31)
        assertEquals(Instant.parse("2026-03-31T10:00:00Z"), next);
        assertTrue(next.isAfter(now));
    }
}

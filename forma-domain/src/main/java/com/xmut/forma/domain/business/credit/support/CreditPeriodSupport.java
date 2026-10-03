package com.xmut.forma.domain.business.credit.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 订阅滚动月：以锚点 UTC 时刻为基准，+N 日历月（无该日则夹到月末）。
 */
public final class CreditPeriodSupport {

    private CreditPeriodSupport() {
    }

    /**
     * 初始下次重置 = 锚点 + 1 日历月。
     */
    public static Instant firstResetAfter(Instant periodAnchor) {
        return addMonthsFromAnchor(periodAnchor, 1);
    }

    /**
     * 已过重置点时：推进到「严格晚于 now」的下一个锚点滚动月边界。
     */
    public static Instant nextResetStrictlyAfter(Instant periodAnchor, Instant now) {
        long months = 1;
        Instant next = addMonthsFromAnchor(periodAnchor, months);
        while (!next.isAfter(now)) {
            months++;
            next = addMonthsFromAnchor(periodAnchor, months);
        }
        return next;
    }

    public static Instant addMonthsFromAnchor(Instant periodAnchor, long months) {
        ZonedDateTime anchor = periodAnchor.atZone(ZoneOffset.UTC);
        return anchor.plusMonths(months).toInstant();
    }
}

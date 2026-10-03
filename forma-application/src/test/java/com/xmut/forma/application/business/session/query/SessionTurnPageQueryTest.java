package com.xmut.forma.application.business.session.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTurnPageQueryTest {

    @Test
    void turnLimitDefaultsAndCaps() {
        assertEquals(20, SessionTurnPageQuery.builder().userId("u").sessionId("s").build().turnLimit());
        assertEquals(20, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(0).build().turnLimit());
        assertEquals(50, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(500).build().turnLimit());
        assertEquals(10, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(10).build().turnLimit());
    }

    @Test
    void nextTokenBlankBecomesNull() {
        assertNull(SessionTurnPageQuery.builder().userId("u").sessionId("s").nextToken(" ").build().nextToken());
        assertEquals("10",
                SessionTurnPageQuery.builder().userId("u").sessionId("s").nextToken("10").build().nextToken());
    }
}

package com.xmut.ebus.application.business.session.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionListQueryTest {

    @Test
    void limitDefaultsAndCaps() {
        assertEquals(50, SessionListQuery.builder().userId("u").build().limit());
        assertEquals(50, SessionListQuery.builder().userId("u").limit(0).build().limit());
        assertEquals(100, SessionListQuery.builder().userId("u").limit(500).build().limit());
        assertEquals(30, SessionListQuery.builder().userId("u").limit(30).build().limit());
    }

    @Test
    void sceneCodeBlankBecomesNull() {
        assertNull(SessionListQuery.builder().userId("u").sceneCode("  ").build().sceneCode());
        assertEquals("ecommerce",
                SessionListQuery.builder().userId("u").sceneCode("ecommerce").build().sceneCode());
    }
}

package com.xmut.ebus.application.business.session.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionLatestArtifactQueryTest {

    @Test
    void artifactTypeBlankBecomesNull() {
        assertNull(SessionLatestArtifactQuery.builder().userId("u").sessionId("s").artifactType("  ").build()
                .artifactType());
        assertNull(SessionLatestArtifactQuery.builder().userId("u").sessionId("s").build().artifactType());
        assertEquals("sku",
                SessionLatestArtifactQuery.builder().userId("u").sessionId("s").artifactType(" sku ").build()
                        .artifactType());
    }
}

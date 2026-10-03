package com.xmut.forma.common.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseQueryTest {

    @Getter
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    private static final class SampleQuery extends BaseQuery {
        private final String marker;
    }

    @Test
    void builderCarriesUserIdAndSubclassField() {
        SampleQuery q = SampleQuery.builder().userId("u-1").marker("m").build();
        assertEquals("u-1", q.getUserId());
        assertEquals("m", q.getMarker());
    }

    @Test
    void userIdMayBeNullForPublicPathsIfEverNeeded() {
        SampleQuery q = SampleQuery.builder().marker("m").build();
        assertNull(q.getUserId());
    }
}

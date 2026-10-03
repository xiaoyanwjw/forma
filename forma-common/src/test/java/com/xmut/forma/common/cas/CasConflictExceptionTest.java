package com.xmut.forma.common.cas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CasConflictExceptionTest {

    @Test
    void defaultMessage() {
        assertEquals("CAS conflict", new CasConflictException().getMessage());
    }

    @Test
    void customMessage() {
        assertEquals("row stale", new CasConflictException("row stale").getMessage());
    }
}

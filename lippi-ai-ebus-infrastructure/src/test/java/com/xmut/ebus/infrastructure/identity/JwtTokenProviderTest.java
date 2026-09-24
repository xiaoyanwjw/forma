package com.xmut.ebus.infrastructure.identity;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("dev-only-change-me-please-32bytes!!", 3_600_000L);

    @Test
    void roundTripSubjectIsUserId() {
        String userId = "22222222-2222-2222-2222-222222222222";
        String token = provider.generateToken(userId);
        assertTrue(provider.validateToken(token));
        assertEquals(userId, provider.getUserIdFromToken(token));
    }

    @Test
    void invalidTokenUnauthorized() {
        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                provider.validateToken("not.a.jwt");
            }
        });
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}

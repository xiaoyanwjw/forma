package com.xmut.forma.interfaces.ratelimit;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentRateLimitInterceptorTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsWhenOverLimit() {
        AgentRateLimitInterceptor interceptor = new AgentRateLimitInterceptor();
        ReflectionTestUtils.setField(interceptor, "maxAttempts", 2);
        ReflectionTestUtils.setField(interceptor, "windowMs", 60_000L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null, Collections.emptyList()));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertTrue(interceptor.preHandle(request, response, new Object()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
    }
}

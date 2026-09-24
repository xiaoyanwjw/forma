package com.xmut.ebus.common.cas;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(CasRetryAspectTest.Cfg.class)
class CasRetryAspectTest {

    @Autowired
    private StubOps stubOps;

    private StubOps target() {
        return AopTestUtils.getUltimateTargetObject(stubOps);
    }

    @BeforeEach
    void resetCounters() {
        target().calls.set(0);
        target().conflictsBeforeSuccess.set(0);
    }

    @Test
    void retriesUntilSuccess() {
        target().conflictsBeforeSuccess.set(2);
        assertEquals("ok", stubOps.flaky());
        assertEquals(3, target().calls.get());
    }

    @Test
    void exhaustedBecomesBusinessException() {
        target().conflictsBeforeSuccess.set(100);
        BusinessException ex = assertThrows(BusinessException.class, stubOps::alwaysConflict);
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getErrorCode());
        assertEquals("积分预占冲突，请稍后重试", ex.getMessage());
        assertEquals(3, target().calls.get());
    }

    @Test
    void businessExceptionNotRetried() {
        BusinessException ex = assertThrows(BusinessException.class, stubOps::businessFail);
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        assertEquals(1, target().calls.get());
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class Cfg {
        @Bean
        CasRetryAspect casRetryAspect() {
            return new CasRetryAspect();
        }

        @Bean
        StubOps stubOps() {
            return new StubOps();
        }
    }

    static class StubOps {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger conflictsBeforeSuccess = new AtomicInteger();

        @CasRetry(maxAttempts = 3)
        public String flaky() {
            calls.incrementAndGet();
            if (conflictsBeforeSuccess.getAndDecrement() > 0) {
                throw new CasConflictException();
            }
            return "ok";
        }

        @CasRetry(maxAttempts = 3, exhaustedMessage = "积分预占冲突，请稍后重试")
        public void alwaysConflict() {
            calls.incrementAndGet();
            throw new CasConflictException();
        }

        @CasRetry(maxAttempts = 5)
        public void businessFail() {
            calls.incrementAndGet();
            throw new BusinessException(ErrorCode.CREDIT_INSUFFICIENT);
        }
    }
}

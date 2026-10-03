package com.xmut.forma.interfaces.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    @Test
    void rejectsWhenWindowExceeded() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("ip1", 3, 60_000));
        assertTrue(limiter.tryAcquire("ip1", 3, 60_000));
        assertTrue(limiter.tryAcquire("ip1", 3, 60_000));
        assertFalse(limiter.tryAcquire("ip1", 3, 60_000));
    }

    @Test
    void isolatesKeys() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("a", 1, 60_000));
        assertFalse(limiter.tryAcquire("a", 1, 60_000));
        assertTrue(limiter.tryAcquire("b", 1, 60_000));
    }

    @Test
    void removesEmptyWindowAfterEvict() throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertTrue(limiter.tryAcquire("gone", 5, 30));
        assertEquals(1, limiter.keyCount());
        Thread.sleep(40);
        // maxAttempts=0：清空后 size>=0 拒绝，且不应把空 deque 留在 map
        assertFalse(limiter.tryAcquire("gone", 0, 30));
        assertEquals(0, limiter.keyCount());
    }
}

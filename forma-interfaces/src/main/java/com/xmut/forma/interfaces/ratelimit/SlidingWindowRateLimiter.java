package com.xmut.forma.interfaces.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口限流（无 Redis）。
 */
public class SlidingWindowRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<String, Deque<Long>>();

    public boolean tryAcquire(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<Long>());
        synchronized (deque) {
            evictExpired(deque, now, windowMs);
            if (deque.isEmpty()) {
                windows.remove(key, deque);
            }
            if (deque.size() >= maxAttempts) {
                return false;
            }
            windows.putIfAbsent(key, deque);
            deque.addLast(now);
            return true;
        }
    }

    /** 测试用：清空状态。 */
    public void reset() {
        windows.clear();
    }

    /** 测试用：当前窗口键数量。 */
    int keyCount() {
        return windows.size();
    }

    private static void evictExpired(Deque<Long> deque, long now, long windowMs) {
        Iterator<Long> it = deque.iterator();
        while (it.hasNext()) {
            Long ts = it.next();
            if (ts == null || now - ts >= windowMs) {
                it.remove();
            } else {
                break;
            }
        }
    }
}

package com.xmut.forma.interfaces.ratelimit;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.interfaces.security.SecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Agent 生成/续跑按用户限流（interfaces 层）。
 */
@Component
public class AgentRateLimitInterceptor implements HandlerInterceptor {

    private final SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter();

    @Value("${agent.rate-limit.max-attempts:10}")
    private int maxAttempts;

    @Value("${agent.rate-limit.window-ms:60000}")
    private long windowMs;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = SecuritySupport.requireUserId();
        if (!rateLimiter.tryAcquire("agent:" + userId, maxAttempts, windowMs)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return true;
    }

    /** 测试用。 */
    public void reset() {
        rateLimiter.reset();
    }
}

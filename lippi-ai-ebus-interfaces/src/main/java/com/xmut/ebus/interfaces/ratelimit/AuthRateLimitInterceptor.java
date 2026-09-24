package com.xmut.ebus.interfaces.ratelimit;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 注册/登录按 IP 限流（interfaces 层）。
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter();

    @Value("${auth.rate-limit.max-attempts:20}")
    private int maxAttempts;

    @Value("${auth.rate-limit.window-ms:60000}")
    private long windowMs;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = "auth:" + resolveClientIp(request);
        if (!rateLimiter.tryAcquire(key, maxAttempts, windowMs)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return true;
    }

    /** 测试用。 */
    public void reset() {
        rateLimiter.reset();
    }

    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }
}

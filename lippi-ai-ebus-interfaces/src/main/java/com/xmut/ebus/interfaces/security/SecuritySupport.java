package com.xmut.ebus.interfaces.security;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 取当前操作者（Controller 组 Command 时注入 BaseCommand）。
 * <p>
 * 近端 JWT 仅有 userId（principal）；username claim 未带时返回 null。
 */
public final class SecuritySupport {

    private SecuritySupport() {
    }

    public static String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return String.valueOf(authentication.getPrincipal());
    }

    /**
     * 当前操作者用户名；JWT 未携带时为 null（后续可扩 claim 或查库）。
     */
    public static String currentUsername() {
        return null;
    }
}

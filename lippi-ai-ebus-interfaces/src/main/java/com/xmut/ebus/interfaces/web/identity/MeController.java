package com.xmut.ebus.interfaces.web.identity;

import com.xmut.ebus.application.identity.dto.MeDTO;
import com.xmut.ebus.application.identity.query.IdentityQueryService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户自检（受保护；可作为未授权证明）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MeController {

    private final IdentityQueryService identityQueryService;

    @GetMapping("/me")
    public ApiResponse<MeDTO> me() {
        String userId = currentUserId();
        return ApiResponse.success(identityQueryService.findMe(userId));
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return String.valueOf(authentication.getPrincipal());
    }
}

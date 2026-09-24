package com.xmut.ebus.interfaces.web.business.credit;

import com.xmut.ebus.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.ebus.application.business.credit.query.CreditQueryService;
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
 * 积分额度查询（仅 GET；预占/结算/释放不开放公开写 REST）。
 */
@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditQueryService creditQueryService;

    @GetMapping
    public ApiResponse<CreditBalanceDTO> getCredits() {
        String userId = currentUserId();
        return ApiResponse.success(creditQueryService.findByUserId(userId));
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

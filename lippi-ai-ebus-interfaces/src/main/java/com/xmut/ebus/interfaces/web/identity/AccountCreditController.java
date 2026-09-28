package com.xmut.ebus.interfaces.web.identity;

import com.xmut.ebus.application.business.credit.dto.CreditUsageDTO;
import com.xmut.ebus.application.business.credit.query.CreditQueryService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户页用量只读面（挂 /api/v1/account，与 {@link AccountController} 分离以免 Identity 依赖 Credit）。
 * 不含预占 / 结算 / 改档写口。
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountCreditController {

    private final CreditQueryService creditQueryService;

    @GetMapping("/credits/usage")
    public ApiResponse<CreditUsageDTO> getCreditUsage() {
        return ApiResponse.success(creditQueryService.findUsage(SecuritySupport.requireUserId()));
    }
}

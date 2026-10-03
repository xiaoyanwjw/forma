package com.xmut.forma.interfaces.web.business.credit;

import com.xmut.forma.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.forma.application.business.credit.query.CreditQueryService;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
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
        return ApiResponse.success(creditQueryService.findByUserId(SecuritySupport.requireUserId()));
    }
}

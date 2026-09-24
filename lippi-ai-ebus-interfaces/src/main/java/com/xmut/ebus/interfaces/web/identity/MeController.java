package com.xmut.ebus.interfaces.web.identity;

import com.xmut.ebus.application.identity.dto.MeDTO;
import com.xmut.ebus.application.identity.query.IdentityQueryService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
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
        return ApiResponse.success(identityQueryService.findMe(SecuritySupport.requireUserId()));
    }
}

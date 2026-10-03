package com.xmut.forma.interfaces.web.identity;

import com.xmut.forma.application.identity.dto.MeDTO;
import com.xmut.forma.application.identity.query.IdentityQueryService;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.interfaces.security.SecuritySupport;
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

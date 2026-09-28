package com.xmut.ebus.interfaces.web.identity;

import com.xmut.ebus.application.identity.command.ChangePasswordCommand;
import com.xmut.ebus.application.identity.command.UpdateUsernameCommand;
import com.xmut.ebus.application.identity.dto.AccountProfileDTO;
import com.xmut.ebus.application.identity.query.IdentityQueryService;
import com.xmut.ebus.application.identity.service.IdentityApplicationService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import com.xmut.ebus.interfaces.vo.identity.ChangeAccountPasswordRequest;
import com.xmut.ebus.interfaces.vo.identity.UpdateAccountProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 账户页主契约：资料读/写、改密（邮箱只读；username 可改）。不含积分写口。
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final IdentityQueryService identityQueryService;
    private final IdentityApplicationService identityApplicationService;

    @GetMapping("/profile")
    public ApiResponse<AccountProfileDTO> getProfile() {
        return ApiResponse.success(identityQueryService.findProfile(SecuritySupport.requireUserId()));
    }

    @PatchMapping("/profile")
    public ApiResponse<AccountProfileDTO> updateProfile(@Valid @RequestBody UpdateAccountProfileRequest request) {
        UpdateUsernameCommand command = UpdateUsernameCommand.builder()
                .userId(SecuritySupport.requireUserId())
                .username(request.getUsername())
                .build();
        return ApiResponse.success(identityApplicationService.updateUsername(command));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangeAccountPasswordRequest request) {
        ChangePasswordCommand command = ChangePasswordCommand.builder()
                .userId(SecuritySupport.requireUserId())
                .username(SecuritySupport.currentUsername())
                .oldPassword(request.getOldPassword())
                .newPassword(request.getNewPassword())
                .build();
        identityApplicationService.changePassword(command);
        return ApiResponse.success("密码已更新", null);
    }
}

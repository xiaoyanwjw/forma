package com.xmut.ebus.interfaces.web.identity;

import com.xmut.ebus.application.identity.command.LoginCommand;
import com.xmut.ebus.application.identity.command.RegisterCommand;
import com.xmut.ebus.application.identity.dto.LoginResultDTO;
import com.xmut.ebus.application.identity.dto.RegisterResultDTO;
import com.xmut.ebus.application.identity.service.IdentityApplicationService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.vo.identity.LoginRequest;
import com.xmut.ebus.interfaces.vo.identity.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 公开注册 / 登录。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IdentityApplicationService identityApplicationService;

    @PostMapping("/register")
    public ApiResponse<RegisterResultDTO> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand();
        command.setUsername(request.getUsername());
        command.setEmail(request.getEmail());
        command.setPassword(request.getPassword());
        return ApiResponse.success(identityApplicationService.register(command));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResultDTO> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand();
        command.setAccount(request.getAccount());
        command.setPassword(request.getPassword());
        return ApiResponse.success(identityApplicationService.login(command));
    }
}

package com.xmut.ebus.application.identity.service;

import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.identity.command.LoginCommand;
import com.xmut.ebus.application.identity.command.RegisterCommand;
import com.xmut.ebus.application.identity.dto.LoginResultDTO;
import com.xmut.ebus.application.identity.dto.RegisterResultDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.identity.model.User;
import com.xmut.ebus.domain.identity.port.JwtTokenPort;
import com.xmut.ebus.domain.identity.port.PasswordHasher;
import com.xmut.ebus.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity 写用例：注册 / 登录签发 JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityApplicationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenPort jwtTokenPort;
    private final CreditApplicationService creditApplicationService;

    @Transactional(rollbackFor = Exception.class)
    public RegisterResultDTO register(RegisterCommand command) {
        if (!command.isAgreedToAiDisclaimer()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "须先确认已知悉：AI 生成内容须人工复核后再上架，Adam 不承诺销售效果");
        }
        String username = StringUtils.requireHasText(command.getUsername(), "用户名不能为空");
        String email = StringUtils.requireHasText(command.getEmail(), "邮箱不能为空").toLowerCase();
        String password = StringUtils.requireHasText(command.getPassword(), "密码不能为空");
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被占用");
        }

        Instant now = Instant.now();
        String userId = UUID.randomUUID().toString();
        User user = User.create(userId, username, email, passwordHasher.hash(password), now);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名或邮箱已被占用");
        }

        // 同事务建免费账本；失败则注册整体回滚
        creditApplicationService.initFreeAccount(userId, now);

        LoggerUtils.success(log, IdentityApplicationService.class, "register",
                NameValue.create("userId", userId));
        return new RegisterResultDTO(userId, username, email);
    }

    @Transactional(readOnly = true)
    public LoginResultDTO login(LoginCommand command) {
        String account = StringUtils.requireHasText(command.getAccount(), "账号不能为空");
        String password = StringUtils.requireHasText(command.getPassword(), "密码不能为空");
        if (account.contains("@")) {
            account = account.toLowerCase();
        }

        User user = userRepository.findByUsernameOrEmail(account)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS));

        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        String token = jwtTokenPort.generateToken(user.getId());
        LoggerUtils.success(log, IdentityApplicationService.class, "login",
                NameValue.create("userId", user.getId()));
        return new LoginResultDTO(token, user.getId(), user.getUsername(), user.getEmail());
    }
}

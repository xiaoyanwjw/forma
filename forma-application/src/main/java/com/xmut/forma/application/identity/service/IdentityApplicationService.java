package com.xmut.forma.application.identity.service;

import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.application.identity.command.ChangePasswordCommand;
import com.xmut.forma.application.identity.command.LoginCommand;
import com.xmut.forma.application.identity.command.RegisterCommand;
import com.xmut.forma.application.identity.command.UpdateUsernameCommand;
import com.xmut.forma.application.identity.dto.AccountProfileDTO;
import com.xmut.forma.application.identity.dto.LoginResultDTO;
import com.xmut.forma.application.identity.dto.RegisterResultDTO;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.identity.model.User;
import com.xmut.forma.domain.identity.port.JwtTokenPort;
import com.xmut.forma.domain.identity.port.PasswordHasher;
import com.xmut.forma.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Identity 写用例：注册 / 登录签发 JWT / 改用户名 / 改密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityApplicationService {

    private static final int USERNAME_MIN_LEN = 2;
    private static final int USERNAME_MAX_LEN = 64;
    private static final int PASSWORD_MIN_LEN = 6;
    private static final int PASSWORD_MAX_LEN = 72;

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
        String username = requireValidUsername(command.getUsername());
        String email = StringUtils.requireHasText(command.getEmail(), "邮箱不能为空").toLowerCase();
        String password = requireValidNewPassword(command.getPassword());

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

    @Transactional(rollbackFor = Exception.class)
    public AccountProfileDTO updateUsername(UpdateUsernameCommand command) {
        String userId = StringUtils.requireHasText(command.getUserId(), "用户未登录");
        String newUsername = requireValidUsername(command.getUsername());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录"));

        if (newUsername.equals(user.getUsername())) {
            return new AccountProfileDTO(user.getId(), user.getUsername(), user.getEmail());
        }

        Optional<User> taken = userRepository.findByUsername(newUsername);
        if (taken.isPresent() && !taken.get().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }

        Instant now = Instant.now();
        try {
            if (!userRepository.updateUsername(userId, newUsername, now)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }

        LoggerUtils.success(log, IdentityApplicationService.class, "updateUsername",
                NameValue.create("userId", userId),
                NameValue.create("username", newUsername));
        return new AccountProfileDTO(user.getId(), newUsername, user.getEmail());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordCommand command) {
        String userId = StringUtils.requireHasText(command.getUserId(), "用户未登录");
        String oldPassword = StringUtils.requireHasText(command.getOldPassword(), "当前密码不能为空");
        String newPassword = requireValidNewPassword(command.getNewPassword());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录"));

        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新密码不能与当前密码相同");
        }

        Instant now = Instant.now();
        String newHash = passwordHasher.hash(newPassword);
        if (!userRepository.updatePasswordHash(userId, newHash, now)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录");
        }

        LoggerUtils.success(log, IdentityApplicationService.class, "changePassword",
                NameValue.create("userId", userId));
    }

    private static String requireValidUsername(String username) {
        String value = StringUtils.requireHasText(username, "用户名不能为空");
        if (value.length() < USERNAME_MIN_LEN || value.length() > USERNAME_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名长度需在 2～64 之间");
        }
        if (value.contains("@")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名不能包含 @");
        }
        return value;
    }

    private static String requireValidNewPassword(String password) {
        String value = StringUtils.requireHasText(password, "密码不能为空");
        if (value.length() < PASSWORD_MIN_LEN) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }
        if (value.length() > PASSWORD_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "密码最长 72 位");
        }
        return value;
    }
}

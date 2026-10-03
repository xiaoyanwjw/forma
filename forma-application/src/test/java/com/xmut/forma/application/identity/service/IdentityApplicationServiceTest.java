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
import com.xmut.forma.domain.identity.model.User;
import com.xmut.forma.domain.identity.port.JwtTokenPort;
import com.xmut.forma.domain.identity.port.PasswordHasher;
import com.xmut.forma.domain.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private JwtTokenPort jwtTokenPort;
    @Mock
    private CreditApplicationService creditApplicationService;

    private IdentityApplicationService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationService(
                userRepository, passwordHasher, jwtTokenPort, creditApplicationService);
    }

    @Test
    void registerPersistsUuidUserWithHashedPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("secret1")).thenReturn("HASH");

        RegisterCommand cmd = RegisterCommand.builder()
                .username("alice")
                .email("alice@example.com")
                .password("secret1")
                .agreedToAiDisclaimer(true)
                .build();

        RegisterResultDTO result = service.register(cmd);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(result.getUserId(), saved.getId());
        assertTrue(UUID.fromString(saved.getId()).toString().equals(saved.getId()));
        assertEquals("HASH", saved.getPasswordHash());
        assertEquals("alice@example.com", saved.getEmail());
        verify(creditApplicationService).initFreeAccount(eq(result.getUserId()), any(Instant.class));
    }

    @Test
    void registerDuplicateUsernameConflicts() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sampleUser()));

        RegisterCommand cmd = RegisterCommand.builder()
                .username("alice")
                .email("other@example.com")
                .password("secret1")
                .agreedToAiDisclaimer(true)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.register(cmd);
            }
        });
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
        verify(creditApplicationService, never()).initFreeAccount(anyString(), any(Instant.class));
    }

    @Test
    void registerConcurrentUniqueKeyMapsToConflict() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("secret1")).thenReturn("HASH");
        doThrow(new DataIntegrityViolationException("uk")).when(userRepository).save(any(User.class));

        RegisterCommand cmd = RegisterCommand.builder()
                .username("alice")
                .email("alice@example.com")
                .password("secret1")
                .agreedToAiDisclaimer(true)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.register(cmd);
            }
        });
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(creditApplicationService, never()).initFreeAccount(anyString(), any(Instant.class));
    }

    @Test
    void registerRejectsWhenDisclaimerNotAgreed() {
        RegisterCommand cmd = RegisterCommand.builder()
                .username("alice")
                .email("alice@example.com")
                .password("secret1")
                .agreedToAiDisclaimer(false)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.register(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("人工复核"));
        verify(userRepository, never()).save(any(User.class));
        verify(creditApplicationService, never()).initFreeAccount(anyString(), any(Instant.class));
    }

    @Test
    void loginWrongPasswordUnifiedMessage() {
        User user = sampleUser();
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("bad", "HASH")).thenReturn(false);

        LoginCommand cmd = LoginCommand.builder()
                .account("alice")
                .password("bad")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.login(cmd);
            }
        });
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(jwtTokenPort, never()).generateToken(anyString());
    }

    @Test
    void loginByEmailReturnsJwt() {
        User user = sampleUser();
        when(userRepository.findByUsernameOrEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret1", "HASH")).thenReturn(true);
        when(jwtTokenPort.generateToken(user.getId())).thenReturn("jwt-token");

        LoginCommand cmd = LoginCommand.builder()
                .account("Alice@Example.com")
                .password("secret1")
                .build();

        LoginResultDTO result = service.login(cmd);
        assertEquals("jwt-token", result.getToken());
        assertEquals(user.getId(), result.getUserId());
    }

    @Test
    void updateUsernamePersistsNewName() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(userRepository.updateUsername(eq(user.getId()), eq("bob"), any(Instant.class))).thenReturn(true);

        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(user.getId())
                .username("bob")
                .build();

        AccountProfileDTO result = service.updateUsername(cmd);

        assertEquals("bob", result.getUsername());
        assertEquals(user.getEmail(), result.getEmail());
        assertEquals(user.getId(), result.getUserId());
        verify(userRepository).updateUsername(eq(user.getId()), eq("bob"), any(Instant.class));
        verify(creditApplicationService, never()).initFreeAccount(anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameRejectsBlank() {
        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(sampleUser().getId())
                .username("   ")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameRejectsAtSign() {
        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(sampleUser().getId())
                .username("a@b")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("@"));
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameRejectsTooShort() {
        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(sampleUser().getId())
                .username("a")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameConflictsWhenTaken() {
        User user = sampleUser();
        User other = User.create("22222222-2222-2222-2222-222222222222", "bob", "bob@example.com", "HASH", Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(other));

        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(user.getId())
                .username("bob")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("用户名已被占用", ex.getMessage());
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameAllowsKeepingOwnName() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(user.getId())
                .username("alice")
                .build();

        AccountProfileDTO result = service.updateUsername(cmd);
        assertEquals("alice", result.getUsername());
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void updateUsernameConcurrentUniqueKeyMapsToConflict() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("uk"))
                .when(userRepository).updateUsername(eq(user.getId()), eq("bob"), any(Instant.class));

        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(user.getId())
                .username("bob")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals("用户名已被占用", ex.getMessage());
    }

    @Test
    void updateUsernameRejectsTooLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            sb.append('a');
        }
        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(sampleUser().getId())
                .username(sb.toString())
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(userRepository, never()).updateUsername(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void updateUsernameWhenNoRowUpdatedUnauthorized() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(userRepository.updateUsername(eq(user.getId()), eq("bob"), any(Instant.class))).thenReturn(false);

        UpdateUsernameCommand cmd = UpdateUsernameCommand.builder()
                .userId(user.getId())
                .username("bob")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.updateUsername(cmd);
            }
        });
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals("用户不存在或未登录", ex.getMessage());
    }

    @Test
    void changePasswordPersistsNewHash() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret12", "HASH")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("NEW_HASH");
        when(userRepository.updatePasswordHash(eq(user.getId()), eq("NEW_HASH"), any(Instant.class))).thenReturn(true);

        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(user.getId())
                .oldPassword("secret12")
                .newPassword("newpass1")
                .build();

        service.changePassword(cmd);

        verify(userRepository).updatePasswordHash(eq(user.getId()), eq("NEW_HASH"), any(Instant.class));
        verify(jwtTokenPort, never()).generateToken(anyString());
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "HASH")).thenReturn(false);

        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(user.getId())
                .oldPassword("wrong")
                .newPassword("newpass1")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.changePassword(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals("当前密码不正确", ex.getMessage());
        verify(userRepository, never()).updatePasswordHash(anyString(), anyString(), any(Instant.class));
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    void changePasswordRejectsTooShortNewPassword() {
        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(sampleUser().getId())
                .oldPassword("secret12")
                .newPassword("12345")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.changePassword(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals("密码至少 6 位", ex.getMessage());
        verify(userRepository, never()).updatePasswordHash(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void changePasswordRejectsTooLongNewPassword() {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 73; i++) {
            tooLong.append('a');
        }
        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(sampleUser().getId())
                .oldPassword("secret12")
                .newPassword(tooLong.toString())
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.changePassword(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals("密码最长 72 位", ex.getMessage());
        verify(userRepository, never()).updatePasswordHash(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void changePasswordRejectsSameAsOld() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret12", "HASH")).thenReturn(true);

        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(user.getId())
                .oldPassword("secret12")
                .newPassword("secret12")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.changePassword(cmd);
            }
        });
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        assertEquals("新密码不能与当前密码相同", ex.getMessage());
        verify(userRepository, never()).updatePasswordHash(anyString(), anyString(), any(Instant.class));
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    void changePasswordWhenNoRowUpdatedUnauthorized() {
        User user = sampleUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret12", "HASH")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("NEW_HASH");
        when(userRepository.updatePasswordHash(eq(user.getId()), eq("NEW_HASH"), any(Instant.class))).thenReturn(false);

        ChangePasswordCommand cmd = ChangePasswordCommand.builder()
                .userId(user.getId())
                .oldPassword("secret12")
                .newPassword("newpass1")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                service.changePassword(cmd);
            }
        });
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals("用户不存在或未登录", ex.getMessage());
    }

    private static User sampleUser() {
        return User.create("11111111-1111-1111-1111-111111111111", "alice", "alice@example.com", "HASH", Instant.now());
    }
}

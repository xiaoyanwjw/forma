package com.xmut.ebus.application.identity.service;

import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.application.identity.command.LoginCommand;
import com.xmut.ebus.application.identity.command.RegisterCommand;
import com.xmut.ebus.application.identity.dto.LoginResultDTO;
import com.xmut.ebus.application.identity.dto.RegisterResultDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.identity.model.User;
import com.xmut.ebus.domain.identity.port.JwtTokenPort;
import com.xmut.ebus.domain.identity.port.PasswordHasher;
import com.xmut.ebus.domain.identity.repository.UserRepository;
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

        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setEmail("alice@example.com");
        cmd.setPassword("secret1");

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

        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setEmail("other@example.com");
        cmd.setPassword("secret1");

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

        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setEmail("alice@example.com");
        cmd.setPassword("secret1");

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
    void loginWrongPasswordUnifiedMessage() {
        User user = sampleUser();
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("bad", "HASH")).thenReturn(false);

        LoginCommand cmd = new LoginCommand();
        cmd.setAccount("alice");
        cmd.setPassword("bad");

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

        LoginCommand cmd = new LoginCommand();
        cmd.setAccount("Alice@Example.com");
        cmd.setPassword("secret1");

        LoginResultDTO result = service.login(cmd);
        assertEquals("jwt-token", result.getToken());
        assertEquals(user.getId(), result.getUserId());
    }

    private static User sampleUser() {
        return User.create("11111111-1111-1111-1111-111111111111", "alice", "alice@example.com", "HASH", Instant.now());
    }
}

package com.xmut.ebus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账户资料 API：覆盖 I/O 矩阵（鉴权 / 改名 / 非法名 / 冲突 / 兼容 / 禁止积分写）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void getProfileReturnsReadonlyEmailAndUsername() throws Exception {
        RegisteredUser user = registerAndLogin("prof");

        mockMvc.perform(get("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.userId))
                .andExpect(jsonPath("$.data.email").value(user.email))
                .andExpect(jsonPath("$.data.username").value(user.username))
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.display_name").doesNotExist());
    }

    @Test
    void getProfileWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/account/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void patchProfileUpdatesUsernameAndAllowsLoginWithNewName() throws Exception {
        RegisteredUser user = registerAndLogin("ren");
        String newUsername = "renamed_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + newUsername + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(newUsername))
                .andExpect(jsonPath("$.data.email").value(user.email))
                .andExpect(jsonPath("$.data.userId").value(user.userId))
                .andExpect(jsonPath("$.data.displayName").doesNotExist());

        mockMvc.perform(get("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(newUsername));

        // 旧 JWT 仍有效（sub=userId）
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(newUsername));

        // 可用新用户名登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + newUsername + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void patchProfileRejectsIllegalUsername() throws Exception {
        RegisteredUser user = registerAndLogin("bad");

        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a@b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("@")));

        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("长度")));

        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            tooLong.append('a');
        }
        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 未落库
        mockMvc.perform(get("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.username));
    }

    @Test
    void patchProfileConflictsWhenUsernameTaken() throws Exception {
        RegisteredUser alice = registerAndLogin("alice");
        RegisteredUser bob = registerAndLogin("bob");

        mockMvc.perform(patch("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + alice.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + bob.username + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("用户名已被占用"));

        mockMvc.perform(get("/api/v1/account/profile")
                        .header("Authorization", "Bearer " + alice.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(alice.username));
    }

    @Test
    void meAndCreditsRemainCompatible() throws Exception {
        RegisteredUser user = registerAndLogin("compat");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.userId))
                .andExpect(jsonPath("$.data.username").value(user.username))
                .andExpect(jsonPath("$.data.email").value(user.email));

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").isNumber());
    }

    @Test
    void accountControllerHasNoCreditWriteEndpoints() {
        Class<?> controller = com.xmut.ebus.interfaces.web.identity.AccountController.class;
        for (Method method : controller.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("credit") || name.contains("reserve")
                            || name.contains("settle") || name.contains("tier") || name.contains("hold"),
                    "account 写面不得含积分结算/改档/预占: " + method.getName());
            Class<?>[] params = method.getParameterTypes();
            assertTrue(Arrays.stream(params).noneMatch(p -> p.getName().toLowerCase().contains("credit")),
                    "account 不得注入积分写参: " + method.getName());
        }
        String sourceHint = Arrays.toString(controller.getDeclaredFields());
        assertFalse(sourceHint.toLowerCase().contains("credit"),
                "AccountController 不得依赖 Credit* 组件: " + sourceHint);
    }

    private RegisteredUser registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"secret12\",\"agreedToAiDisclaimer\":true}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + username + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(login.getResponse().getContentAsString()).path("data");
        return new RegisteredUser(data.path("userId").asText(), username, email, data.path("token").asText());
    }

    private static final class RegisteredUser {
        final String userId;
        final String username;
        final String email;
        final String token;

        RegisteredUser(String userId, String username, String email, String token) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.token = token;
        }
    }
}

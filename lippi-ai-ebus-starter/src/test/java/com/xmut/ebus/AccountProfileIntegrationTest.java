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

    @Autowired
    private com.xmut.ebus.application.business.credit.service.CreditApplicationService creditApplicationService;

    @Autowired
    private com.xmut.ebus.domain.business.credit.repository.CreditHoldRepository creditHoldRepository;

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
    void getCreditUsageReturnsSummaryAndEmptyEntriesForFreshAccount() throws Exception {
        RegisteredUser user = registerAndLogin("usage0");

        mockMvc.perform(get("/api/v1/account/credits/usage")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.available").value(20))
                .andExpect(jsonPath("$.data.monthlyQuota").value(20))
                .andExpect(jsonPath("$.data.used").value(0))
                .andExpect(jsonPath("$.data.nextResetAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entries").isArray())
                .andExpect(jsonPath("$.data.entries").isEmpty());
    }

    @Test
    void getCreditUsageListsOnlySettledHolds() throws Exception {
        RegisteredUser user = registerAndLogin("usage1");
        String holdSettled = creditApplicationService.reserveOne(user.userId);
        creditApplicationService.settle(user.userId, holdSettled);
        String holdReleased = creditApplicationService.reserveOne(user.userId);
        creditApplicationService.release(user.userId, holdReleased);
        creditApplicationService.reserveOne(user.userId); // ACTIVE，不应出现在流水

        mockMvc.perform(get("/api/v1/account/credits/usage")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(18))
                .andExpect(jsonPath("$.data.monthlyQuota").value(20))
                .andExpect(jsonPath("$.data.used").value(2))
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].holdId").value(holdSettled))
                .andExpect(jsonPath("$.data.entries[0].title").value("已扣分"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(1))
                .andExpect(jsonPath("$.data.entries[0].delta").value(-1))
                .andExpect(jsonPath("$.data.entries[0].occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[*].holdId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(holdReleased))));
    }

    @Test
    void getCreditUsageOrdersSettledDescAndRespectsLimit() throws Exception {
        RegisteredUser user = registerAndLogin("usageOrd");
        String olderHold = creditApplicationService.reserveOne(user.userId);
        creditApplicationService.settle(user.userId, olderHold);
        String newerHold = creditApplicationService.reserveOne(user.userId);
        creditApplicationService.settle(user.userId, newerHold);

        java.sql.Timestamp olderAt = java.sql.Timestamp.from(java.time.Instant.parse("2026-09-20T08:00:00Z"));
        java.sql.Timestamp newerAt = java.sql.Timestamp.from(java.time.Instant.parse("2026-09-25T08:00:00Z"));
        jdbcTemplate.update("UPDATE ebus_credit_hold SET updated_at = ? WHERE biz_id = ?", olderAt, olderHold);
        jdbcTemplate.update("UPDATE ebus_credit_hold SET updated_at = ? WHERE biz_id = ?", newerAt, newerHold);

        mockMvc.perform(get("/api/v1/account/credits/usage")
                        .header("Authorization", "Bearer " + user.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].holdId").value(newerHold))
                .andExpect(jsonPath("$.data.entries[1].holdId").value(olderHold));

        java.util.List<com.xmut.ebus.domain.business.credit.model.CreditHold> limited =
                creditHoldRepository.listSettledByUserId(user.userId, 1);
        org.junit.jupiter.api.Assertions.assertEquals(1, limited.size());
        org.junit.jupiter.api.Assertions.assertEquals(newerHold, limited.get(0).getId());
    }

    @Test
    void getCreditUsageWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/account/credits/usage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
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

        Class<?> usageController = com.xmut.ebus.interfaces.web.identity.AccountCreditController.class;
        for (Method method : usageController.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("reserve") || name.contains("settle")
                            || name.contains("release") || name.contains("tier") || name.contains("change"),
                    "account 用量面不得含写积分: " + method.getName());
            assertTrue(method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class),
                    "account 用量面仅允许 GET: " + method.getName());
        }
        boolean hasPostOrPatchOrPutOrDelete = Arrays.stream(usageController.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class));
        assertFalse(hasPostOrPatchOrPutOrDelete, "AccountCreditController 不得暴露写积分 HTTP 动词");
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

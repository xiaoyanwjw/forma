package com.xmut.ebus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.domain.business.credit.support.CreditPeriodSupport;
import com.xmut.ebus.infrastructure.identity.JwtTokenProvider;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @Autowired
    private CreditApplicationService creditApplicationService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.xmut.ebus.application.business.credit.support.CreditAdminAuthorization creditAdminAuthorization;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.<String>emptySet());
        jdbcTemplate.update("DELETE FROM ebus_credit_tier_change");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void registerThenGetCreditsShowsFree20() throws Exception {
        String token = registerAndLogin("cr_" + shortId());

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.available").value(20))
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.nextResetAt").isNotEmpty());
    }

    @Test
    void creditsWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/credits"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reserveSettleDebitsBalance() throws Exception {
        String username = "rs_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);

        String holdId = creditApplicationService.reserveOne(userId);
        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(19))
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.reserved").value(1));

        creditApplicationService.settle(userId, holdId);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(19))
                .andExpect(jsonPath("$.data.balance").value(19))
                .andExpect(jsonPath("$.data.reserved").value(0));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM ebus_credit_hold WHERE biz_id = ?", String.class, holdId);
        assertEquals(CreditHoldStatus.SETTLED.name(), status);

        BusinessException again = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        creditApplicationService.settle(userId, holdId);
                    }
                });
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, again.getErrorCode());
    }

    @Test
    void reserveReleaseRestoresAvailable() throws Exception {
        String username = "rl_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);

        String holdId = creditApplicationService.reserveOne(userId);
        creditApplicationService.release(userId, holdId);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(20))
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.reserved").value(0));
    }

    @Test
    void reserveFailsWhenInsufficient() throws Exception {
        String username = "ins_" + shortId();
        registerAndLogin(username);
        String userId = userIdOf(username);
        jdbcTemplate.update("UPDATE ebus_credit_account SET balance = 0, reserved = 0 WHERE user_id = ?", userId);

        BusinessException ex = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        creditApplicationService.reserveOne(userId);
                    }
                });
        assertEquals(ErrorCode.CREDIT_INSUFFICIENT, ex.getErrorCode());
        assertEquals("积分不足", ex.getMessage());
    }

    @Test
    void lazyMonthlyResetRegrantsQuota() throws Exception {
        String username = "mr_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);

        Instant past = Instant.now().minusSeconds(3600);
        jdbcTemplate.update(
                "UPDATE ebus_credit_account SET balance = 3, reserved = 0, next_reset_at = ? WHERE user_id = ?",
                Timestamp.from(past), userId);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value(CreditTier.FREE.name()))
                .andExpect(jsonPath("$.data.available").value(20))
                .andExpect(jsonPath("$.data.balance").value(20));

        Instant nextReset = jdbcTemplate.queryForObject(
                "SELECT next_reset_at FROM ebus_credit_account WHERE user_id = ?",
                Instant.class, userId);
        assertTrue(nextReset.isAfter(Instant.now()));
    }

    @Test
    void lazyMonthlyResetKeepsReservedAvailableIsQuotaMinusReserved() throws Exception {
        String username = "mrr_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);

        Instant past = Instant.now().minusSeconds(3600);
        jdbcTemplate.update(
                "UPDATE ebus_credit_account SET balance = 3, reserved = 2, next_reset_at = ? WHERE user_id = ?",
                Timestamp.from(past), userId);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.reserved").value(2))
                .andExpect(jsonPath("$.data.available").value(18));
    }

    @Test
    void settleWrongUserIdRejectedWithoutBalanceChange() throws Exception {
        String owner = "own_" + shortId();
        registerAndLogin(owner);
        String ownerId = userIdOf(owner);
        String holdId = creditApplicationService.reserveOne(ownerId);

        Integer balanceBefore = jdbcTemplate.queryForObject(
                "SELECT balance FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId);
        Integer reservedBefore = jdbcTemplate.queryForObject(
                "SELECT reserved FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId);

        String stranger = UUID.randomUUID().toString();
        BusinessException ex = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        creditApplicationService.settle(stranger, holdId);
                    }
                });
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, ex.getErrorCode());

        assertEquals(balanceBefore, jdbcTemplate.queryForObject(
                "SELECT balance FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId));
        assertEquals(reservedBefore, jdbcTemplate.queryForObject(
                "SELECT reserved FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId));
        assertEquals(CreditHoldStatus.ACTIVE.name(), jdbcTemplate.queryForObject(
                "SELECT status FROM ebus_credit_hold WHERE biz_id = ?", String.class, holdId));
    }

    @Test
    void releaseWrongUserIdRejectedWithoutBalanceChange() throws Exception {
        String owner = "rel_" + shortId();
        registerAndLogin(owner);
        String ownerId = userIdOf(owner);
        String holdId = creditApplicationService.reserveOne(ownerId);

        Integer reservedBefore = jdbcTemplate.queryForObject(
                "SELECT reserved FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId);

        BusinessException ex = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        creditApplicationService.release(UUID.randomUUID().toString(), holdId);
                    }
                });
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, ex.getErrorCode());
        assertEquals(reservedBefore, jdbcTemplate.queryForObject(
                "SELECT reserved FROM ebus_credit_account WHERE user_id = ?", Integer.class, ownerId));
        assertEquals(CreditHoldStatus.ACTIVE.name(), jdbcTemplate.queryForObject(
                "SELECT status FROM ebus_credit_hold WHERE biz_id = ?", String.class, holdId));
    }

    @Test
    void legacyUserWithoutAccountGetsCompensatedViaAuthenticatedGet() throws Exception {
        String userId = UUID.randomUUID().toString();
        String username = "legacy_" + shortId();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO ebus_user (biz_id, username, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                userId, username, username + "@example.com",
                "$2a$10$abcdefghijklmnopqrstuu", Timestamp.from(now), Timestamp.from(now));

        String token = jwtTokenProvider.generateToken(userId);
        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.available").value(20))
                .andExpect(jsonPath("$.data.balance").value(20));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_account WHERE user_id = ?", Integer.class, userId);
        assertEquals(1, count);
    }

    @Test
    void concurrentReserveDoesNotExceedAvailable() throws Exception {
        String username = "cc_" + shortId();
        registerAndLogin(username);
        String userId = userIdOf(username);
        jdbcTemplate.update("UPDATE ebus_credit_account SET balance = 3, reserved = 0 WHERE user_id = ?", userId);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 10; i++) {
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        creditApplicationService.reserveOne(userId);
                        success.incrementAndGet();
                    } catch (BusinessException ex) {
                        if (ex.getErrorCode() == ErrorCode.CREDIT_INSUFFICIENT) {
                            insufficient.incrementAndGet();
                        } else {
                            throw ex;
                        }
                    }
                    return null;
                }
            });
        }
        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(3, success.get());
        assertEquals(7, insufficient.get());
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved FROM ebus_credit_account WHERE user_id = ?", Integer.class, userId);
        assertEquals(3, reserved);
    }

    @Test
    void adminChangeTierFreeToProVisibleOnGetCredits() throws Exception {
        String adminName = "adm_" + shortId();
        String adminToken = registerAndLogin(adminName);
        String adminId = userIdOf(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(adminId));

        String targetName = "tgt_" + shortId();
        String targetToken = registerAndLogin(targetName);
        String targetId = userIdOf(targetName);

        Instant before = Instant.now().minusSeconds(1);
        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.balance").value(200))
                .andExpect(jsonPath("$.data.available").value(200));

        Instant anchor = jdbcTemplate.queryForObject(
                "SELECT period_anchor_at FROM ebus_credit_account WHERE user_id = ?",
                Instant.class, targetId);
        Instant expectedNextReset = CreditPeriodSupport.firstResetAfter(anchor);
        Instant nextReset = jdbcTemplate.queryForObject(
                "SELECT next_reset_at FROM ebus_credit_account WHERE user_id = ?",
                Instant.class, targetId);
        assertTrue(!anchor.isBefore(before));
        assertEquals(expectedNextReset, nextReset);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.balance").value(200))
                .andExpect(jsonPath("$.data.available").value(200))
                .andExpect(jsonPath("$.data.nextResetAt").value(expectedNextReset.toString()));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_tier_change WHERE target_user_id = ? AND operator_user_id = ?"
                        + " AND from_tier = 'FREE' AND to_tier = 'PRO'",
                Integer.class, targetId, adminId);
        assertEquals(1, auditCount);
    }

    @Test
    void adminChangeTierInvalidTargetTierReturns400() throws Exception {
        String adminName = "adm_bad_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));
        String targetName = "tgt_bad_" + shortId();
        registerAndLogin(targetName);
        String targetId = userIdOf(targetName);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("套餐档位无效"));

        assertEquals("FREE", jdbcTemplate.queryForObject(
                "SELECT tier FROM ebus_credit_account WHERE user_id = ?", String.class, targetId));
        assertEquals(20, jdbcTemplate.queryForObject(
                "SELECT balance FROM ebus_credit_account WHERE user_id = ?", Integer.class, targetId));
    }

    @Test
    void adminChangeTierMissingTargetUserNoOrphanAccount() throws Exception {
        String adminName = "adm_miss_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));
        String missingId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + missingId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("目标用户不存在"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_account WHERE user_id = ?", Integer.class, missingId));
    }

    @Test
    void adminChangeTierProToPlus() throws Exception {
        String adminName = "adm2_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));
        String targetName = "tgt2_" + shortId();
        String targetToken = registerAndLogin(targetName);
        String targetId = userIdOf(targetName);
        jdbcTemplate.update("UPDATE ebus_credit_account SET tier = 'PRO', balance = 200 WHERE user_id = ?", targetId);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PLUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PLUS"))
                .andExpect(jsonPath("$.data.balance").value(600));

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PLUS"))
                .andExpect(jsonPath("$.data.available").value(600));
    }

    @Test
    void adminChangeTierSameTierIdempotentNoSecondAudit() throws Exception {
        String adminName = "adm3_" + shortId();
        String adminToken = registerAndLogin(adminName);
        String adminId = userIdOf(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(adminId));
        String targetName = "tgt3_" + shortId();
        registerAndLogin(targetName);
        String targetId = userIdOf(targetName);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.balance").value(200));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_tier_change WHERE target_user_id = ?",
                Integer.class, targetId);
        assertEquals(1, auditCount);
    }

    @Test
    void adminChangeTierRejectsDowngrade() throws Exception {
        String adminName = "adm4_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));
        String targetName = "tgt4_" + shortId();
        registerAndLogin(targetName);
        String targetId = userIdOf(targetName);
        jdbcTemplate.update("UPDATE ebus_credit_account SET tier = 'PRO', balance = 200 WHERE user_id = ?", targetId);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"FREE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅允许升级套餐，不能降级"));

        assertEquals("PRO", jdbcTemplate.queryForObject(
                "SELECT tier FROM ebus_credit_account WHERE user_id = ?", String.class, targetId));
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_tier_change WHERE target_user_id = ?",
                Integer.class, targetId);
        assertEquals(0, auditCount);
    }

    @Test
    void adminChangeTierForbiddenWhenNotWhitelisted() throws Exception {
        String name = "nowl_" + shortId();
        String token = registerAndLogin(name);
        String targetId = userIdOf(name);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isForbidden());

        assertEquals("FREE", jdbcTemplate.queryForObject(
                "SELECT tier FROM ebus_credit_account WHERE user_id = ?", String.class, targetId));
    }

    @Test
    void adminChangeTierUnauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + UUID.randomUUID() + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminChangeTierEnsureReadyThenUpgradeForLegacyUser() throws Exception {
        String adminName = "adm5_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));

        String legacyId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO ebus_user (biz_id, username, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                legacyId, "leg_" + shortId(), "leg_" + shortId() + "@example.com",
                "$2a$10$abcdefghijklmnopqrstuu", Timestamp.from(now), Timestamp.from(now));

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + legacyId + "\",\"targetTier\":\"PLUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PLUS"))
                .andExpect(jsonPath("$.data.balance").value(600));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_account WHERE user_id = ?", Integer.class, legacyId));
    }

    @Test
    void adminChangeTierKeepsReservedEvenIfExceedsNewQuota() throws Exception {
        String adminName = "adm6_" + shortId();
        String adminToken = registerAndLogin(adminName);
        creditAdminAuthorization.replaceAllowedUserIds(java.util.Collections.singleton(userIdOf(adminName)));
        String targetName = "tgt6_" + shortId();
        String targetToken = registerAndLogin(targetName);
        String targetId = userIdOf(targetName);
        jdbcTemplate.update(
                "UPDATE ebus_credit_account SET balance = 20, reserved = 250 WHERE user_id = ?", targetId);

        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(200))
                .andExpect(jsonPath("$.data.reserved").value(250))
                .andExpect(jsonPath("$.data.available").value(-50));

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(-50));
    }

    private String registerAndLogin(String username) throws Exception {
        String email = username + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"secret12\",\"agreedToAiDisclaimer\":true}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + email + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    private String userIdOf(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT biz_id FROM ebus_user WHERE username = ?", String.class, username);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

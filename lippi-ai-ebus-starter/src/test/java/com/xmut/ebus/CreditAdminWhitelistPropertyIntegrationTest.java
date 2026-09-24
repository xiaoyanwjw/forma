package com.xmut.ebus;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用不经 replaceAllowedUserIds 的 {@code credit.admin.user-ids} 属性覆盖白名单接线。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "credit.admin.user-ids=" + CreditAdminWhitelistPropertyIntegrationTest.ADMIN_ID)
class CreditAdminWhitelistPropertyIntegrationTest {

    static final String ADMIN_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM ebus_credit_tier_change");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void propertyWhitelistAllowsAdminWithoutReplace() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO ebus_user (id, username, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                ADMIN_ID, "prop_admin", "prop_admin@example.com",
                "$2a$10$abcdefghijklmnopqrstuu", Timestamp.from(now), Timestamp.from(now));
        String targetId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO ebus_user (id, username, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                targetId, "prop_tgt", "prop_tgt@example.com",
                "$2a$10$abcdefghijklmnopqrstuu", Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO ebus_credit_account (id, user_id, tier, balance, reserved, period_anchor_at, next_reset_at, version, created_at, updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), targetId, "FREE", 20, 0,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(86400L * 30)), 0,
                Timestamp.from(now), Timestamp.from(now));

        String token = jwtTokenProvider.generateToken(ADMIN_ID);
        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + targetId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PRO"))
                .andExpect(jsonPath("$.data.balance").value(200));

        assertEquals("PRO", jdbcTemplate.queryForObject(
                "SELECT tier FROM ebus_credit_account WHERE user_id = ?", String.class, targetId));
    }

    @Test
    void propertyWhitelistRejectsUnlistedJwt() throws Exception {
        Instant now = Instant.now();
        String strangerId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO ebus_user (id, username, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                strangerId, "prop_str", "prop_str@example.com",
                "$2a$10$abcdefghijklmnopqrstuu", Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO ebus_credit_account (id, user_id, tier, balance, reserved, period_anchor_at, next_reset_at, version, created_at, updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), strangerId, "FREE", 20, 0,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(86400L * 30)), 0,
                Timestamp.from(now), Timestamp.from(now));

        String token = jwtTokenProvider.generateToken(strangerId);
        mockMvc.perform(post("/api/v1/admin/credits/change-tier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + strangerId + "\",\"targetTier\":\"PRO\"}"))
                .andExpect(status().isForbidden());

        assertEquals("FREE", jdbcTemplate.queryForObject(
                "SELECT tier FROM ebus_credit_account WHERE user_id = ?", String.class, strangerId));
    }
}

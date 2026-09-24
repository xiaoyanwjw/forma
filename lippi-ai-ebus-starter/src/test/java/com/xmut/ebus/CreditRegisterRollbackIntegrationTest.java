package com.xmut.ebus;

import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册与建账同事务：建账失败时用户行须回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditRegisterRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @MockBean
    private CreditApplicationService creditApplicationService;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM ebus_credit_tier_change");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
        doThrow(new RuntimeException("credit init failed"))
                .when(creditApplicationService).initFreeAccount(anyString(), any(Instant.class));
    }

    @Test
    void registerRollsBackUserWhenCreditInitFails() throws Exception {
        String username = "rb_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@example.com\",\"password\":\"secret12\"}"))
                .andExpect(status().isInternalServerError());

        Integer users = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_user WHERE username = ?", Integer.class, username);
        assertEquals(0, users);
        Integer accounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_credit_account", Integer.class);
        assertEquals(0, accounts);
    }
}

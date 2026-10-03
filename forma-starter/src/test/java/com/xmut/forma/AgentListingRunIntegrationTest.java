package com.xmut.forma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.interfaces.ratelimit.AuthRateLimitInterceptor;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentListingRunIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM forma_artifact");
        jdbcTemplate.update("DELETE FROM forma_media_object");
        jdbcTemplate.update("DELETE FROM forma_generation_run");
        jdbcTemplate.update("DELETE FROM forma_credit_tier_change");
        jdbcTemplate.update("DELETE FROM forma_credit_hold");
        jdbcTemplate.update("DELETE FROM forma_credit_account");
        jdbcTemplate.update("DELETE FROM forma_user");
    }

    @Test
    void listingRunWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs/listing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"帮我写上架素材\",\"sceneCode\":\"ecommerce\"}")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forma_generation_run", Integer.class);
        assertEquals(0, runCount.intValue());
    }

    @Test
    void listingRunInsufficientCreditsDoesNotCreateRun() throws Exception {
        String username = "ls0_" + shortId();
        String token = registerAndLogin(username);
        String userId = jdbcTemplate.queryForObject(
                "SELECT biz_id FROM forma_user WHERE username = ?", String.class, username);
        jdbcTemplate.update("UPDATE forma_credit_account SET balance = 0, reserved = 0 WHERE user_id = ?", userId);

        mockMvc.perform(post("/api/v1/agent/runs/listing")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"帮我写上架素材\",\"sceneCode\":\"ecommerce\"}")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").exists());

        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forma_generation_run", Integer.class);
        assertEquals(0, runCount.intValue());
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
        JsonNode root = objectMapper.readTree(login.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

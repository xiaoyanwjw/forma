package com.xmut.ebus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.ebus.domain.business.credit.constant.CreditHoldStatus;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentEmptyRunIntegrationTest {

    private static final Set<String> AD4_NAMES = new HashSet<String>(Arrays.asList(
            "run_started", "message_delta", "tool_started", "tool_finished",
            "artifact_ready", "run_failed", "run_settled"
    ));

    private static final Pattern EVENT_NAME = Pattern.compile("(?m)^event:(.+)$");

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
        jdbcTemplate.update("DELETE FROM ebus_generation_run");
        jdbcTemplate.update("DELETE FROM ebus_credit_tier_change");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void emptyRunWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs/empty")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_generation_run", Integer.class);
        assertEquals(0, runCount.intValue());
    }

    @Test
    void emptyRunInsufficientCreditsDoesNotCreateRun() throws Exception {
        String username = "ag0_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        jdbcTemplate.update("UPDATE ebus_credit_account SET balance = 0, reserved = 0 WHERE user_id = ?", userId);

        mockMvc.perform(post("/api/v1/agent/runs/empty")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isPaymentRequired());

        Integer runCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_generation_run", Integer.class);
        assertEquals(0, runCount.intValue());
    }

    @Test
    void emptyRunStreamsAd4EventsReleasesHoldAndDoesNotSettle() throws Exception {
        String username = "ag1_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);

        MvcResult async = mockMvc.perform(post("/api/v1/agent/runs/empty")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> eventNames = parseEventNames(body);
        assertTrue(eventNames.contains("run_started"), body);
        assertTrue(eventNames.contains("message_delta"), body);
        assertTrue(eventNames.contains("run_failed"), body);
        assertFalse(eventNames.contains("artifact_ready"), body);
        assertFalse(eventNames.contains("run_settled"), body);
        for (String name : eventNames) {
            assertTrue(AD4_NAMES.contains(name), "unexpected event: " + name);
        }

        Map<String, Object> run = jdbcTemplate.queryForMap(
                "SELECT biz_id, hold_id, session_id, artifact_ref, status FROM ebus_generation_run WHERE user_id = ?",
                userId);
        assertEquals(GenerationRunStatus.FAILED.name(), run.get("status"));
        assertTrue(run.get("artifact_ref") == null || "".equals(run.get("artifact_ref")));
        String holdId = String.valueOf(run.get("hold_id"));
        String holdStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM ebus_credit_hold WHERE biz_id = ?", String.class, holdId);
        assertEquals(CreditHoldStatus.RELEASED.name(), holdStatus);

        mockMvc.perform(get("/api/v1/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.available").value(20))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.balance").value(20))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.reserved").value(0));
    }

    private List<String> parseEventNames(String sseBody) {
        List<String> names = new ArrayList<String>();
        Matcher matcher = EVENT_NAME.matcher(sseBody);
        while (matcher.find()) {
            names.add(matcher.group(1).trim());
        }
        return names;
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

    private String userIdOf(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT biz_id FROM ebus_user WHERE username = ?", String.class, username);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

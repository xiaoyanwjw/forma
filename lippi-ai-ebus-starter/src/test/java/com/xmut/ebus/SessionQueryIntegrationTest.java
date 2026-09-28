package com.xmut.ebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.interfaces.ratelimit.AuthRateLimitInterceptor;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.session.SessionStore;
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
import java.util.Arrays;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionQueryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @Autowired
    private SessionStore sessionStore;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM pi_session_entry");
        jdbcTemplate.update("DELETE FROM pi_session");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void listAndMessagesHonorUserAclAndSkipNullOwner() throws Exception {
        String ownerName = "sq_" + shortId();
        String otherName = "sqo_" + shortId();
        String token = registerAndLogin(ownerName);
        String otherToken = registerAndLogin(otherName);
        String ownerId = userIdOf(ownerName);
        String otherId = userIdOf(otherName);

        String mine = "sess-mine-" + shortId();
        String theirs = "sess-theirs-" + shortId();
        String orphan = "sess-orphan-" + shortId();
        Instant now = Instant.now();
        insertSession(mine, ownerId, now);
        insertSession(theirs, otherId, now);
        insertSession(orphan, null, now);

        sessionStore.append(mine, "run-mine", Arrays.asList(
                Message.user("找杯子"),
                Message.assistant("这是建议", null)));
        sessionStore.append(theirs, "run-theirs", Arrays.asList(
                Message.user("不该看见")));

        mockMvc.perform(get("/api/v1/sessions").param("sceneCode", "ecommerce")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sessionId").value(mine))
                .andExpect(jsonPath("$.data[0].title").value("找杯子"))
                .andExpect(jsonPath("$.data[0].sceneCode").value("ecommerce"));

        mockMvc.perform(get("/api/v1/sessions/" + mine + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("找杯子"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"));

        mockMvc.perform(get("/api/v1/sessions/" + theirs + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/sessions/" + orphan + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sessionId").value(theirs));
    }

    private void insertSession(String sessionId, String userId, Instant updatedAt) {
        Timestamp ts = Timestamp.from(updatedAt);
        jdbcTemplate.update(
                "INSERT INTO pi_session (session_id, user_id, scene_code, source, status, "
                        + "compact_anchor_seq, message_count, created_at, updated_at) "
                        + "VALUES (?, ?, 'ecommerce', 'api', 'active', 0, 0, ?, ?)",
                sessionId, userId, ts, ts);
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
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private String userIdOf(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT biz_id FROM ebus_user WHERE username = ?", String.class, username);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

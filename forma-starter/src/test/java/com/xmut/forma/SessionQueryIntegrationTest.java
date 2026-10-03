package com.xmut.forma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.forma.application.business.session.query.SessionLatestArtifactQuery;
import com.xmut.forma.application.business.session.query.SessionQueryService;
import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.forma.interfaces.ratelimit.AuthRateLimitInterceptor;
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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Autowired
    private SessionQueryService sessionQueryService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM pi_session_entry");
        jdbcTemplate.update("DELETE FROM pi_session");
        jdbcTemplate.update("DELETE FROM forma_artifact");
        jdbcTemplate.update("DELETE FROM forma_generation_run");
        jdbcTemplate.update("DELETE FROM forma_credit_hold");
        jdbcTemplate.update("DELETE FROM forma_credit_account");
        jdbcTemplate.update("DELETE FROM forma_user");
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
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextToken").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].runId").value("run-mine"))
                .andExpect(jsonPath("$.data.items[0].userPrompt").value("找杯子"))
                .andExpect(jsonPath("$.data.items[0].messages.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.items[0].messages[0].content").value("找杯子"))
                .andExpect(jsonPath("$.data.items[0].messages[0].seq").isNumber())
                .andExpect(jsonPath("$.data.items[0].messages[1].role").value("assistant"));

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

    @Test
    void messagesPageByTipSeqAcrossTwoLogicalRunsIncludingHitl() throws Exception {
        String ownerName = "sqp_" + shortId();
        String token = registerAndLogin(ownerName);
        String ownerId = userIdOf(ownerName);
        String sessionId = "sess-page-" + shortId();
        insertSession(sessionId, ownerId, Instant.now());

        sessionStore.append(sessionId, "run-old", Arrays.asList(
                Message.user("先问"),
                Message.assistant("先答", null)));
        sessionStore.append(sessionId, "run-hitl:suspend", Arrays.asList(
                Message.user("请生成上架素材"),
                Message.assistant("plan", null)));
        sessionStore.append(sessionId, "run-hitl:resume", Arrays.asList(
                Message.user("{\"optionId\":\"confirm_execute\"}"),
                Message.assistant("exec", null)));

        MvcResult first = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/messages")
                        .param("limit", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].runId").value("run-hitl"))
                .andExpect(jsonPath("$.data.items[0].userPrompt").value("请生成上架素材"))
                .andExpect(jsonPath("$.data.items[0].messages.length()").value(4))
                .andReturn();
        String nextToken = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("nextToken").asText();
        assertTrue(nextToken != null && !nextToken.isEmpty() && !"null".equals(nextToken));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/messages")
                        .param("limit", "1")
                        .param("nextToken", nextToken)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].runId").value("run-old"))
                .andExpect(jsonPath("$.data.items[0].userPrompt").value("先问"))
                .andExpect(jsonPath("$.data.nextToken").value(nullValue()));
    }

    @Test
    void prepareRunRefusesForeignSessionIdBeforeReserve() throws Exception {
        String ownerName = "sqf_" + shortId();
        String otherName = "sqx_" + shortId();
        registerAndLogin(ownerName);
        String otherToken = registerAndLogin(otherName);
        String ownerId = userIdOf(ownerName);
        String otherId = userIdOf(otherName);
        String sessionId = "sess-foreign-" + shortId();
        insertSession(sessionId, ownerId, Instant.now());

        mockMvc.perform(post("/api/v1/agent/runs/empty")
                        .param("sessionId", sessionId)
                        .param("sceneCode", "ecommerce")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("会话不存在或无权查看"));

        Integer holds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forma_credit_hold WHERE user_id = ?", Integer.class, otherId);
        assertEquals(0, holds.intValue());
        Integer runs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forma_generation_run WHERE session_id = ?", Integer.class, sessionId);
        assertEquals(0, runs.intValue());
    }

    @Test
    void latestArtifactSkipsChatAndPicksLatestPicklistOrSku() throws Exception {
        String username = "sqa_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        String sessionId = "sess-art-" + shortId();
        insertSession(sessionId, userId, Instant.now());

        Instant t1 = Instant.parse("2026-09-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t3 = Instant.parse("2026-09-22T10:00:00Z");
        String pickId = insertArtifactAndRun(userId, sessionId, ArtifactType.PICKLIST, "旧选品", t1);
        insertArtifactAndRun(userId, sessionId, ArtifactType.CHAT, "聊天", t3);
        String skuId = insertArtifactAndRun(userId, sessionId, ArtifactType.SKU, "新 Listing", t2);

        HistoryArtifactDetailDTO latest = sessionQueryService.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(userId).sessionId(sessionId).build())
                .orElse(null);
        assertTrue(latest != null);
        assertEquals(skuId, latest.getId());
        assertEquals("sku", latest.getArtifactType());
        assertEquals(sessionId, latest.getSessionId());
        assertFalse(pickId.equals(latest.getId()));
        assertFalse(sessionQueryService.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(userId).sessionId("no-such-session").build())
                .isPresent());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/latest-artifact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(skuId))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId));
    }

    @Test
    void latestArtifactSkipsOutOfWindowAndFallsBackToRecent() throws Exception {
        String username = "sqw_" + shortId();
        registerAndLogin(username);
        String userId = userIdOf(username);
        String sessionId = "sess-win-" + shortId();
        insertSession(sessionId, userId, Instant.now());

        Instant tooOld = Instant.now().minus(70, ChronoUnit.DAYS);
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        String oldSkuId = insertArtifactAndRun(userId, sessionId, ArtifactType.SKU, "超窗 Listing", tooOld);
        jdbcTemplate.update(
                "UPDATE forma_generation_run SET created_at = ? WHERE artifact_ref = ?",
                Timestamp.from(Instant.now()), oldSkuId);
        String pickId = insertArtifactAndRun(userId, sessionId, ArtifactType.PICKLIST, "窗内选品", recent);

        HistoryArtifactDetailDTO latest = sessionQueryService.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(userId).sessionId(sessionId).build())
                .orElse(null);
        assertTrue(latest != null);
        assertEquals(pickId, latest.getId());
        assertEquals("picklist", latest.getArtifactType());
    }

    @Test
    void latestArtifactEmptyWhenOnlyOutOfWindow() throws Exception {
        String username = "sqe_" + shortId();
        registerAndLogin(username);
        String userId = userIdOf(username);
        String sessionId = "sess-old-" + shortId();
        insertSession(sessionId, userId, Instant.now());
        insertArtifactAndRun(userId, sessionId, ArtifactType.SKU, "超窗",
                Instant.now().minus(70, ChronoUnit.DAYS));

        assertFalse(sessionQueryService.getLatestArtifact(
                SessionLatestArtifactQuery.builder().userId(userId).sessionId(sessionId).build())
                .isPresent());
    }

    private String insertArtifactAndRun(String userId,
                                        String sessionId,
                                        ArtifactType type,
                                        String title,
                                        Instant createdAt) {
        String artifactId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String payload = "{\"view\":{\"kind\":\"doc\",\"title\":\"" + title
                + "\",\"blocks\":[]},\"data\":{}}";
        artifactRepository.save(Artifact.create(
                artifactId, userId, runId, type, "ecommerce", null, title, payload, createdAt));
        Timestamp ts = Timestamp.from(createdAt);
        jdbcTemplate.update(
                "INSERT INTO forma_generation_run (biz_id, user_id, hold_id, session_id, scene_code, "
                        + "artifact_ref, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ecommerce', ?, 'SETTLED', ?, ?)",
                runId, userId, UUID.randomUUID().toString(), sessionId, artifactId, ts, ts);
        return artifactId;
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
                "SELECT biz_id FROM forma_user WHERE username = ?", String.class, username);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

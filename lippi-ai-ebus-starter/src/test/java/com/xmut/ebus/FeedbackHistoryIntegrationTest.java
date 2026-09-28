package com.xmut.ebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @Autowired
    private ArtifactRepository artifactRepository;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM ebus_feedback");
        jdbcTemplate.update("DELETE FROM ebus_artifact");
        jdbcTemplate.update("DELETE FROM ebus_credit_hold");
        jdbcTemplate.update("DELETE FROM ebus_credit_account");
        jdbcTemplate.update("DELETE FROM ebus_user");
    }

    @Test
    void feedbackDoesNotChangeCreditsAndIsReadable() throws Exception {
        String username = "fb_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        String artifactId = saveOwnedArtifact(userId, ArtifactType.PICKLIST, Instant.now(), "选品 A");

        int availableBefore = availableCredits(token);

        MvcResult created = mockMvc.perform(post("/api/v1/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifactId\":\"" + artifactId + "\",\"tag\":\"质量差\",\"commentText\":\"偏水\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("质量差"))
                .andExpect(jsonPath("$.data.commentText").value("偏水"))
                .andExpect(jsonPath("$.data.artifactId").value(artifactId))
                .andReturn();

        String feedbackId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();
        assertTrue(feedbackId != null && !feedbackId.isEmpty());

        assertEquals(availableBefore, availableCredits(token));

        mockMvc.perform(get("/api/v1/feedbacks/" + feedbackId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(feedbackId))
                .andExpect(jsonPath("$.data.tag").value("质量差"));
    }

    @Test
    void upsertGoodThenPoorKeepsOneRowAndGetByArtifact() throws Exception {
        String username = "fbup_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        String artifactId = saveOwnedArtifact(userId, ArtifactType.PICKLIST, Instant.now(), "选品 upsert");

        mockMvc.perform(post("/api/v1/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifactId\":\"" + artifactId + "\",\"tag\":\"质量好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("质量好"));

        MvcResult updated = mockMvc.perform(post("/api/v1/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifactId\":\"" + artifactId + "\",\"tag\":\"质量差\",\"commentText\":\"偏水\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("质量差"))
                .andExpect(jsonPath("$.data.commentText").value("偏水"))
                .andReturn();

        String feedbackId = objectMapper.readTree(updated.getResponse().getContentAsString())
                .path("data").path("id").asText();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ebus_feedback WHERE artifact_id = ?", Integer.class, artifactId);
        assertEquals(1, rows);

        mockMvc.perform(get("/api/v1/feedbacks").param("artifactId", artifactId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(feedbackId))
                .andExpect(jsonPath("$.data.tag").value("质量差"))
                .andExpect(jsonPath("$.data.commentText").value("偏水"));

        mockMvc.perform(get("/api/v1/feedbacks").param("artifactId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void feedbackOnOthersArtifactForbidden() throws Exception {
        String token = registerAndLogin("fb2_" + shortId());
        String otherId = UUID.randomUUID().toString();
        String artifactId = saveOwnedArtifact(otherId, ArtifactType.SKU, Instant.now(), "他人 Listing");

        mockMvc.perform(post("/api/v1/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artifactId\":\"" + artifactId + "\",\"tag\":\"质量差\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyListsOwnRecentPicklistAndSkuOnly() throws Exception {
        String username = "hi_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        Instant now = Instant.now();
        String pickId = saveOwnedArtifact(userId, ArtifactType.PICKLIST, now.minus(1, ChronoUnit.HOURS), "选品近");
        String skuId = saveOwnedArtifact(userId, ArtifactType.SKU, now.minus(2, ChronoUnit.HOURS), "上架近");
        saveOwnedArtifact(userId, ArtifactType.CHAT, now.minus(3, ChronoUnit.HOURS), "聊天不应出现");
        saveOwnedArtifact(userId, ArtifactType.LISTING_PLAN, now.minus(4, ChronoUnit.HOURS), "策划不应出现");
        saveOwnedArtifact(userId, ArtifactType.PICKLIST, now.minus(70, ChronoUnit.DAYS), "超窗不应出现");
        saveOwnedArtifact(UUID.randomUUID().toString(), ArtifactType.PICKLIST, now, "他人不应出现");

        mockMvc.perform(get("/api/v1/history/artifacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(pickId))
                .andExpect(jsonPath("$.data[1].id").value(skuId));

        mockMvc.perform(get("/api/v1/history/artifacts").param("sceneCode", "ecommerce")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/history/artifacts").param("sceneCode", "no-such-scene")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void historyDetailResignsViewAndKeepsOldArtifactAfterSecondSave() throws Exception {
        String username = "hd_" + shortId();
        String token = registerAndLogin(username);
        String userId = userIdOf(username);
        Instant now = Instant.now();
        String firstId = saveOwnedArtifact(userId, ArtifactType.PICKLIST, now.minus(1, ChronoUnit.HOURS), "旧成果");
        String secondId = saveOwnedArtifact(userId, ArtifactType.PICKLIST, now, "新成果");

        mockMvc.perform(get("/api/v1/history/artifacts/" + firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstId))
                .andExpect(jsonPath("$.data.title").value("旧成果"))
                .andExpect(jsonPath("$.data.view").isMap());

        mockMvc.perform(get("/api/v1/history/artifacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[1].id").value(firstId));
    }

    @Test
    void historyDetailOthersForbidden() throws Exception {
        String token = registerAndLogin("hx_" + shortId());
        String otherArtifact = saveOwnedArtifact(UUID.randomUUID().toString(), ArtifactType.PICKLIST,
                Instant.now(), "他人");

        mockMvc.perform(get("/api/v1/history/artifacts/" + otherArtifact)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String saveOwnedArtifact(String userId, ArtifactType type, Instant createdAt, String title) {
        String id = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String payload = "{\"view\":{\"kind\":\"doc\",\"title\":\"" + title
                + "\",\"blocks\":[]},\"data\":{}}";
        Artifact artifact = Artifact.create(
                id, userId, runId, type, "ecommerce", null, title, payload, createdAt);
        artifactRepository.save(artifact);
        return id;
    }

    private int availableCredits(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/credits")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("available").asInt();
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

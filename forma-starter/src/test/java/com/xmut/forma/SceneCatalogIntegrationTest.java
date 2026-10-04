package com.xmut.forma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SceneCatalogIntegrationTest {

    private static final String SEED_ECOMMERCE_BIZ_ID = "a1000001-0001-4000-8000-000000000001";
    private static final String SEED_XIAOHONGSHU_BIZ_ID = "a1000001-0001-4000-8000-000000000003";
    private static final String SEED_SHORT_VIDEO_BIZ_ID = "a1000001-0001-4000-8000-000000000002";
    private static final String SEED_TECH_DIGEST_BIZ_ID = "a1000001-0001-4000-8000-000000000005";
    private static final String SEED_SPORTS_GEAR_BIZ_ID = "a1000001-0001-4000-8000-000000000006";
    private static final String SEED_LOCAL_LIFE_BIZ_ID = "a1000001-0001-4000-8000-000000000004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @BeforeEach
    void cleanUsersOnly() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM forma_credit_tier_change");
        jdbcTemplate.update("DELETE FROM forma_credit_hold");
        jdbcTemplate.update("DELETE FROM forma_credit_account");
        jdbcTemplate.update("DELETE FROM forma_user");
        jdbcTemplate.update("DELETE FROM forma_scene WHERE scene_code NOT IN (?,?,?,?,?,?)",
                "ecommerce", "xiaohongshu", "short_video", "tech_digest", "sports_gear", "weekend_trip");
    }

    @Test
    void listScenesWithJwtReturnsSixOrderedSeedRows() throws Exception {
        String token = registerAndLogin("sc_" + shortId());

        mockMvc.perform(get("/api/v1/scenes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].bizId").value(SEED_TECH_DIGEST_BIZ_ID))
                .andExpect(jsonPath("$.data[0].sceneCode").value("tech_digest"))
                .andExpect(jsonPath("$.data[0].category").value("tech"))
                .andExpect(jsonPath("$.data[0].displayName").value("科技速读"))
                .andExpect(jsonPath("$.data[0].status").value("COMING_SOON"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data[1].bizId").value(SEED_ECOMMERCE_BIZ_ID))
                .andExpect(jsonPath("$.data[1].sceneCode").value("ecommerce"))
                .andExpect(jsonPath("$.data[1].category").value("ecommerce"))
                .andExpect(jsonPath("$.data[1].displayName").value("电商开店"))
                .andExpect(jsonPath("$.data[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data[1].summary").isNotEmpty())
                .andExpect(jsonPath("$.data[2].bizId").value(SEED_XIAOHONGSHU_BIZ_ID))
                .andExpect(jsonPath("$.data[2].sceneCode").value("xiaohongshu"))
                .andExpect(jsonPath("$.data[2].category").value("content"))
                .andExpect(jsonPath("$.data[2].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[2].sortOrder").value(3))
                .andExpect(jsonPath("$.data[3].bizId").value(SEED_SHORT_VIDEO_BIZ_ID))
                .andExpect(jsonPath("$.data[3].sceneCode").value("short_video"))
                .andExpect(jsonPath("$.data[3].category").value("content"))
                .andExpect(jsonPath("$.data[3].status").value("COMING_SOON"))
                .andExpect(jsonPath("$.data[3].sortOrder").value(4))
                .andExpect(jsonPath("$.data[4].bizId").value(SEED_SPORTS_GEAR_BIZ_ID))
                .andExpect(jsonPath("$.data[4].sceneCode").value("sports_gear"))
                .andExpect(jsonPath("$.data[4].category").value("sports"))
                .andExpect(jsonPath("$.data[4].displayName").value("装备选购对比"))
                .andExpect(jsonPath("$.data[4].status").value("COMING_SOON"))
                .andExpect(jsonPath("$.data[4].sortOrder").value(5))
                .andExpect(jsonPath("$.data[5].bizId").value(SEED_LOCAL_LIFE_BIZ_ID))
                .andExpect(jsonPath("$.data[5].sceneCode").value("weekend_trip"))
                .andExpect(jsonPath("$.data[5].displayName").value("周末行程"))
                .andExpect(jsonPath("$.data[5].category").value("life"))
                .andExpect(jsonPath("$.data[5].status").value("COMING_SOON"))
                .andExpect(jsonPath("$.data[5].sortOrder").value(6));
    }

    @Test
    void listScenesWithoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/scenes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listEcommerceSkillCapsulesReturnsLabelAndExampleWithoutSkillBody() throws Exception {
        String token = registerAndLogin("sc_launch_" + shortId());

        MvcResult result = mockMvc.perform(get("/api/v1/scenes/ecommerce/skills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("ecommerce"))
                .andExpect(jsonPath("$.data.skills.length()").value(2))
                .andExpect(jsonPath("$.data.skills[0].skillId").value("ecommerce-picklist"))
                .andExpect(jsonPath("$.data.skills[0].label").value("选品清单"))
                .andExpect(jsonPath("$.data.skills[0].examplePrompt").isNotEmpty())
                .andExpect(jsonPath("$.data.skills[1].skillId").value("ecommerce-skulist"))
                .andExpect(jsonPath("$.data.skills[1].label").value("生成素材"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("allowed-tools"));
        assertFalse(body.contains("Workflow"));
        assertFalse(body.contains("search_sku"));
    }

    @Test
    void listXiaohongshuSkillCapsulesReturnsThreeOrderedCapsules() throws Exception {
        String token = registerAndLogin("sc_xhs_launch_" + shortId());

        mockMvc.perform(get("/api/v1/scenes/xiaohongshu/skills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("xiaohongshu"))
                .andExpect(jsonPath("$.data.skills.length()").value(3))
                .andExpect(jsonPath("$.data.skills[0].skillId").value("xhs-topiclist"))
                .andExpect(jsonPath("$.data.skills[0].label").value("选题清单"))
                .andExpect(jsonPath("$.data.skills[1].skillId").value("xhs-note"))
                .andExpect(jsonPath("$.data.skills[2].skillId").value("xhs-break"));
    }


    @Test
    void listScenesResponseHasNoPromptOrToolFields() throws Exception {
        String token = registerAndLogin("sc_shape_" + shortId());

        MvcResult result = mockMvc.perform(get("/api/v1/scenes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(data.isArray());
        assertEquals(6, data.size());

        for (JsonNode item : data) {
            assertTrue(item.has("category"));
            assertFalse(item.has("prompt"));
            assertFalse(item.has("systemPrompt"));
            assertFalse(item.has("tool"));
            assertFalse(item.has("tools"));
            assertFalse(item.has("skill"));
            assertFalse(item.has("skills"));
            Iterator<String> names = item.fieldNames();
            while (names.hasNext()) {
                String name = names.next().toLowerCase();
                assertFalse(name.contains("prompt"), "unexpected field: " + name);
                assertFalse(name.contains("tool"), "unexpected field: " + name);
                assertFalse(name.contains("skill"), "unexpected field: " + name);
            }
        }
    }

    @Test
    void extraSceneAppearsOnNextListWithoutApiChange() throws Exception {
        String token = registerAndLogin("sc_extra_" + shortId());
        String extraBizId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update(
                    "INSERT INTO forma_scene (biz_id, scene_code, display_name, category, status, sort_order, summary, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    extraBizId, "extra_scene_" + shortId(), "额外场景", "tech", "COMING_SOON", 99,
                    "可扩展验证行", Timestamp.from(now), Timestamp.from(now));

            mockMvc.perform(get("/api/v1/scenes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(7))
                    .andExpect(jsonPath("$.data[6].bizId").value(extraBizId))
                    .andExpect(jsonPath("$.data[6].category").value("tech"))
                    .andExpect(jsonPath("$.data[6].sortOrder").value(99))
                    .andExpect(jsonPath("$.data[6].status").value("COMING_SOON"));
        } finally {
            jdbcTemplate.update("DELETE FROM forma_scene WHERE biz_id = ?", extraBizId);
        }
    }

    @Test
    void duplicateSceneCodeRejectedByUniqueConstraint() {
        Instant now = Instant.now();
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO forma_scene (biz_id, scene_code, display_name, category, status, sort_order, summary, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), "ecommerce", "重复码", "ecommerce", "AVAILABLE", 10,
                "不应写入", Timestamp.from(now), Timestamp.from(now)));
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

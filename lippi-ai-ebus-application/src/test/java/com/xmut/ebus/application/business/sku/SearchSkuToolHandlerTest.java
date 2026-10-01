package com.xmut.ebus.application.business.sku;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.application.business.agent.tool.sku.MockSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.application.business.agent.tool.sku.SkuReranker;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchPort;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearcher;
import com.xmut.ebus.application.config.PiToolCatalogConfiguration;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SearchSkuToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SearchSkuToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = handlerFor(new MockSkuSearchClient());
    }

    @Test
    void searchXiangxunReturnsHttpsDetailUrls() throws Exception {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", "香薰");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "search_sku", args),
                new ToolContext("r1", "t1"));

        assertTrue(result.isSuccess());
        assertEquals("search_sku", result.getToolName());
        JsonNode root = MAPPER.readTree(result.getOutput());
        JsonNode hits = root.has("hits") ? root.get("hits") : root;
        assertTrue(hits.isArray());
        assertTrue(hits.size() >= 1);
        for (JsonNode hit : hits) {
            assertTrue(hit.hasNonNull("detailUrl"));
            assertTrue(hit.get("detailUrl").asText().startsWith("https://"));
            assertTrue(hit.get("title").asText().contains("香薰"));
        }
    }

    @Test
    void emptyHitsReturnsFailedResult() {
        handler = handlerFor((query, platform, pageSize) -> Collections.emptyList());
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", "无货关键词");
        ToolResult result = handler.handle(
                new ToolCallEntry("c-empty", "search_sku", args),
                new ToolContext("r1", "t1"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null && result.getErrorMessage().contains("empty"));
    }

    @Test
    void emptyQueryReturnsFailedResultWithoutThrowing() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", "");
        ToolResult result;
        try {
            result = handler.handle(
                    new ToolCallEntry("c2", "search_sku", args),
                    new ToolContext("r1", "t1"));
        } catch (RuntimeException ex) {
            fail("empty query must not throw: " + ex);
            return;
        }
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null && !result.getErrorMessage().trim().isEmpty());
    }

    @Test
    void catalogResolvesSearchSku() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        PiToolCatalogConfiguration.class)
                .withBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class, () -> {
                    InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
                    skills.sealBootstrap();
                    return skills;
                })
                .run(context -> {
                    assertTrue(context.getBean(ToolCatalog.class).resolve("search_sku").isPresent());
                    assertTrue(context.getBean(ToolCatalog.class).resolve("search_xhs_note").isPresent());
                    assertTrue(context.getBean(ToolCatalog.class).resolve("fetch_xhs_note").isPresent());
                    assertTrue(context.getBean(ToolCatalog.class).resolve("read_skill").isPresent());
                    assertTrue(context.getBean(ModelCatalog.class).resolve("ebus.sku.rerank") != null);
                });
    }

    private static SearchSkuToolHandler handlerFor(SkuSearchPort port) {
        return new SearchSkuToolHandler(new SkuSearcher(port, new SkuSearchProperties(), SkuReranker.identity()));
    }
}

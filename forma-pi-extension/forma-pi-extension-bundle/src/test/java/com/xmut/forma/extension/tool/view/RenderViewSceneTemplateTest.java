package com.xmut.forma.extension.tool.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scene Mustache files live on scene jars; this suite runs on the bundle classpath.
 */
class RenderViewSceneTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ViewEnricherComposite sceneRegistry() {
        return ViewEnricherComposite.of(
                new XhsTopiclistViewEnricher(),
                new EcommercePicklistViewEnricher(),
                new XhsBreakViewEnricher(),
                new XhsNoteViewEnricher(),
                new EcommerceSkulistViewEnricher(),
                new TechDigestViewEnricher(),
                new TechCompetitorViewEnricher(),
                new TechBriefingViewEnricher());
    }


    @Test
    void handle_xhs_note_defaults_to_html_format() throws Exception {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("xhs-note")
                .description("note")
                .promptRef("classpath:scenes/xiaohongshu/xhs-note/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new CatalogSkillTemplateLoader(catalog, new DefaultResourceLoader()),
                new MustacheViewRenderer(),
                sceneRegistry());
        Path run = Files.createTempDirectory("render-view-note-");
        String artifact = "{"
                + "\"title\":\"笔记\","
                + "\"body\":\"正文\","
                + "\"titleOptions\":[\"标题一\"],"
                + "\"tags\":[\"桌搭\"],"
                + "\"imageHints\":[\"首图\"]"
                + "}";
        Files.write(run.resolve("artifact.json"), artifact.getBytes(StandardCharsets.UTF_8));

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "xhs-note", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        assertEquals("html", view.get("format").asText());
        String content = view.get("content").asText();
        assertTrue(content.contains("标题备选"));
        assertTrue(content.contains("forma-deck"), content);
        assertTrue(content.contains("forma-tag"), content);
    }

    @Test
    void handle_break_skill_template_renders_handoff_button() throws Exception {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("xhs-break")
                .description("break")
                .promptRef("classpath:scenes/xiaohongshu/xhs-break/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new CatalogSkillTemplateLoader(catalog, new DefaultResourceLoader()),
                new MustacheViewRenderer(),
                sceneRegistry());
        Path run = Files.createTempDirectory("render-view-break-");
        String artifact = "{"
                + "\"title\":\"拆解\","
                + "\"structure\":\"要点\","
                + "\"skeleton\":\"骨架\","
                + "\"rewrite\":\"改写\","
                + "\"targetProduct\":\"拓展坞\""
                + "}";
        Files.write(run.resolve("artifact.json"), artifact.getBytes(StandardCharsets.UTF_8));

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "xhs-break", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        String content = view.get("content").asText();
        assertTrue(content.contains("data-forma-skill-id=\"xhs-note\""));
        assertTrue(content.contains("按骨架写笔记"));
    }

    @Test
    void handle_techDigest_skill_template_renders_minimalArtifact() throws Exception {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("tech-digest")
                .description("digest")
                .promptRef("classpath:scenes/tech_digest/tech-digest/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new CatalogSkillTemplateLoader(catalog, new DefaultResourceLoader()),
                new MustacheViewRenderer(),
                sceneRegistry());
        Path run = Files.createTempDirectory("render-view-tech-digest-");
        Files.write(run.resolve("artifact.json"),
                "{\"title\":\"科技速读\",\"oneLiner\":\"\",\"forWhom\":\"\"}"
                        .getBytes(StandardCharsets.UTF_8));

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "tech-digest", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        String content = view.get("content").asText();
        assertTrue(content.contains("科技速读"));
        assertTrue(content.contains("AI 摘要，请对照原文"));
    }

    @Test
    void handle_techCompetitor_skill_template_renders_statusAndRivals() throws Exception {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("tech-competitor")
                .description("competitor")
                .promptRef("classpath:scenes/tech_product/tech-competitor/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new CatalogSkillTemplateLoader(catalog, new DefaultResourceLoader()),
                new MustacheViewRenderer(),
                sceneRegistry());
        Path run = Files.createTempDirectory("render-view-tech-competitor-");
        String artifact = "{"
                + "\"title\":\"Notion\","
                + "\"oneLiner\":\"一体化协作空间\","
                + "\"source\":\"fetch\","
                + "\"sourceUrl\":\"https://www.notion.so\","
                + "\"excerpts\":[{\"heading\":\"Hero\",\"quotes\":[\"All-in-one workspace\"]}],"
                + "\"snapshot\":{"
                + "\"positioning\":{\"status\":\"found\",\"value\":\"All-in-one workspace\",\"quotes\":[\"All-in-one workspace\"]},"
                + "\"audience\":{\"status\":\"not_public\",\"value\":\"\"},"
                + "\"pricingSignal\":{\"status\":\"not_public\",\"value\":\"\"}"
                + "},"
                + "\"whyPay\":{\"status\":\"found\",\"bullets\":[\"笔记与文档同区\"],\"quotes\":[\"All-in-one workspace\"]},"
                + "\"packaging\":{\"status\":\"not_public\",\"value\":\"\",\"reason\":\"未见价档\"},"
                + "\"growthSignals\":{\"status\":\"not_public\",\"items\":[]},"
                + "\"rivals\":{\"status\":\"inferred\",\"reason\":\"由定位推断\",\"items\":[{\"name\":\"Coda\",\"note\":\"协作文档\"}]},"
                + "\"uncertainties\":[\"需打开 Pricing\"]"
                + "}";
        Files.write(run.resolve("artifact.json"), artifact.getBytes(StandardCharsets.UTF_8));

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "tech-competitor", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        String content = view.get("content").asText();
        assertTrue(content.contains("竞品分析"), content);
        assertTrue(content.contains("Found"), content);
        assertTrue(content.contains("推断"), content);
        assertTrue(content.contains("Coda"), content);
        assertTrue(content.contains("请对照原文核实"), content);
    }

    @Test
    void handle_topiclist_skill_template_renders_handoff_button() throws Exception {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("xhs-topiclist")
                .description("topiclist")
                .promptRef("classpath:scenes/xiaohongshu/xhs-topiclist/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new CatalogSkillTemplateLoader(catalog, new DefaultResourceLoader()),
                new MustacheViewRenderer(),
                sceneRegistry());
        Path run = Files.createTempDirectory("render-view-topiclist-");
        String artifact = "{"
                + "\"title\":\"选题清单\","
                + "\"disclaimer\":\"非实时平台全站行情\","
                + "\"items\":[{\"id\":\"tp-1\",\"title\":\"【优先发】测试选题\","
                + "\"hook\":\"钩子\",\"angle\":\"角度\",\"whyFirst\":\"优先\",\"risk\":\"风险\","
                + "\"sourceNoteUrl\":\"https://www.xiaohongshu.com/explore/example\"}"
                + "]}";
        Files.write(run.resolve("artifact.json"), artifact.getBytes(StandardCharsets.UTF_8));

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "xhs-topiclist", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        String content = view.get("content").asText();
        assertTrue(content.contains("data-forma-skill-id=\"xhs-note\""));
        assertTrue(content.contains("写成笔记"));
        assertTrue(content.contains("测试选题"));
        assertFalse(content.contains("【优先发】"));
    }

    private static ToolCallEntry call(ObjectNode args) {
        return new ToolCallEntry("c1", "render_view", args);
    }
}

package com.xmut.forma.extension.tool.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.Skill;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderViewToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void handle_defaults_writes_v2_view_json() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        byte[] artifact = "{\"title\":\"选题\",\"items\":[{\"id\":\"tp-1\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(run.resolve("artifact.json"), artifact);
        FixedTemplateLoader loader = new FixedTemplateLoader("<h1>{{title}}</h1>");
        RenderViewToolHandler handler = new RenderViewToolHandler(loader, new MustacheViewRenderer());

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "demo-skill", run.toString()));

        assertTrue(result.isSuccess());
        assertEquals("demo-skill", loader.seenSkill);
        assertEquals("template/view.mustache", loader.seenTemplate);
        JsonNode ok = MAPPER.readTree(result.getOutput());
        assertTrue(ok.get("ok").asBoolean());
        assertEquals("view.json", ok.get("out").asText());
        assertEquals(Files.size(run.resolve("view.json")), ok.get("bytes").asLong());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        assertEquals(2, view.get("version").asInt());
        assertEquals("选题", view.get("title").asText());
        assertEquals("html", view.get("format").asText());
        assertEquals("<h1>选题</h1>", view.get("content").asText());
        assertEquals(new String(artifact, StandardCharsets.UTF_8),
                new String(Files.readAllBytes(run.resolve("artifact.json")), StandardCharsets.UTF_8));
    }

    @Test
    void handle_escapes_html_and_defaults_missing_title() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        Files.write(run.resolve("artifact.json"), "{\"hook\":\"A<B\"}".getBytes(StandardCharsets.UTF_8));
        RenderViewToolHandler handler = new RenderViewToolHandler(
                new FixedTemplateLoader("<p>{{hook}}</p>"), new MustacheViewRenderer());

        ToolResult result = handler.handle(
                call(JsonNodeFactory.instance.objectNode()),
                new ToolContext("r1", "t1", "demo-skill", run.toString()));

        assertTrue(result.isSuccess());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("view.json")));
        assertEquals("draft", view.get("title").asText());
        assertEquals("<p>A&lt;B</p>", view.get("content").asText());
    }

    @Test
    void handle_custom_paths_and_markdown_format() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        Files.createDirectories(run.resolve("plan"));
        Files.write(run.resolve("plan/artifact.json"),
                "{\"title\":\"策划\"}".getBytes(StandardCharsets.UTF_8));
        FixedTemplateLoader loader = new FixedTemplateLoader("# {{title}}");
        RenderViewToolHandler handler = new RenderViewToolHandler(loader, new MustacheViewRenderer());
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("artifact", "plan/artifact.json");
        args.put("out", "plan/view.json");
        args.put("template", "template/plan/view.mustache");
        args.put("format", "markdown");

        ToolResult result = handler.handle(
                call(args),
                new ToolContext("r1", "t1", "listing-plan", run.toString()));

        assertTrue(result.isSuccess());
        assertEquals("listing-plan", loader.seenSkill);
        assertEquals("template/plan/view.mustache", loader.seenTemplate);
        JsonNode ok = MAPPER.readTree(result.getOutput());
        assertEquals("plan/view.json", ok.get("out").asText());
        JsonNode view = MAPPER.readTree(Files.readAllBytes(run.resolve("plan/view.json")));
        assertEquals(2, view.get("version").asInt());
        assertEquals("策划", view.get("title").asText());
        assertEquals("markdown", view.get("format").asText());
        assertEquals("# 策划", view.get("content").asText());
    }

    @Test
    void handle_missing_active_skill_fails() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        Files.write(run.resolve("artifact.json"), "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        ToolResult result = new RenderViewToolHandler(
                new FixedTemplateLoader("<p>{{title}}</p>"), new MustacheViewRenderer())
                .handle(call(JsonNodeFactory.instance.objectNode()),
                        new ToolContext("r1", "t1", "  ", run.toString()));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("active skill"));
        assertFalse(Files.exists(run.resolve("view.json")));
    }

    @Test
    void handle_missing_workspace_fails() {
        ToolResult result = new RenderViewToolHandler(
                new FixedTemplateLoader("<p/>"), new MustacheViewRenderer())
                .handle(call(JsonNodeFactory.instance.objectNode()),
                        new ToolContext("r1", "t1", "demo-skill", null));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("workspace root missing"));
    }

    @Test
    void handle_missing_artifact_fails() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        ToolResult result = new RenderViewToolHandler(
                new FixedTemplateLoader("<p/>"), new MustacheViewRenderer())
                .handle(call(JsonNodeFactory.instance.objectNode()),
                        new ToolContext("r1", "t1", "demo-skill", run.toString()));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("artifact file missing"));
    }

    @Test
    void handle_rejects_path_escape() throws Exception {
        Path run = Files.createTempDirectory("render-view-");
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("artifact", "../secret.json");
        ToolResult result = new RenderViewToolHandler(
                new FixedTemplateLoader("<p/>"), new MustacheViewRenderer())
                .handle(call(args), new ToolContext("r1", "t1", "demo-skill", run.toString()));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("escapes"));
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
                new MustacheViewRenderer());
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
                new MustacheViewRenderer());
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
                new MustacheViewRenderer());
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

    @Test
    void catalog_loader_reads_template_next_to_skill_prompt() {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("demo-skill")
                .description("demo")
                .promptRef("classpath:scenes/test/demo-skill/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        CatalogSkillTemplateLoader loader = new CatalogSkillTemplateLoader(
                catalog, new DefaultResourceLoader());

        String template = loader.load("demo-skill", "template/view.mustache");

        assertEquals("<p>{{title}}</p>", template.trim());
    }

    @Test
    void catalog_loader_rejects_template_escape_and_unknown_skill() {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id("demo-skill")
                .description("demo")
                .promptRef("classpath:scenes/test/demo-skill/SKILL.md")
                .allowedTools(Collections.<String>emptyList())
                .build());
        CatalogSkillTemplateLoader loader = new CatalogSkillTemplateLoader(
                catalog, new DefaultResourceLoader());

        try {
            loader.load("missing-skill", "template/view.mustache");
            assertFalse(true, "unknown skill should fail");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("skill not found"));
        }
        try {
            loader.load("demo-skill", "../secret.mustache");
            assertFalse(true, "template escape should fail");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("escapes"));
        }
    }

    private static ToolCallEntry call(ObjectNode args) {
        return new ToolCallEntry("c1", "render_view", args);
    }

    private static final class FixedTemplateLoader implements SkillTemplateLoader {
        private final String template;
        private String seenSkill;
        private String seenTemplate;

        private FixedTemplateLoader(String template) {
            this.template = template;
        }

        @Override
        public String load(String activeSkillId, String templateRelativePath) {
            this.seenSkill = activeSkillId;
            this.seenTemplate = templateRelativePath;
            return template;
        }
    }
}

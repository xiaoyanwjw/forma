package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.extension.config.ViewToolsConfiguration;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceOutputParserTest {

    @TempDir
    Path root;

    @Test
    void order_is_zero() {
        Order order = WorkspaceOutputParser.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(0, order.value());
    }

    @Test
    void idle_chat_without_view_path_does_not_apply() {
        WorkspaceOutputParser parser = parser(skill("demo", null, null, null));

        assertFalse(parser.appliesTo(ctx("demo", root)));
        assertFalse(parser.appliesTo(ctx(null, root)));
        assertFalse(parser.appliesTo(ctx("  ", root)));
        assertFalse(parser.appliesTo(null));
    }

    @Test
    void missing_artifact_path_does_not_apply() {
        WorkspaceOutputParser parser = parser(skill("demo", "view.json", null, null));

        assertFalse(parser.appliesTo(ctx("demo", root)));
    }

    @Test
    void plan_view_path_does_not_apply() {
        WorkspaceOutputParser parser = parser(
                skill("ecommerce-skulist", "exec/view.json", "exec/artifact.json", "plan/view.json"));

        assertFalse(parser.appliesTo(ctx("ecommerce-skulist", root)));
    }

    @Test
    void declared_slots_apply_even_when_files_are_missing() {
        WorkspaceOutputParser parser = parser(skill("demo", "view.json", "artifact.json", null));

        assertTrue(parser.appliesTo(ctx("demo", root)));
    }

    @Test
    void view_json_and_artifact_json_success() throws Exception {
        Files.write(root.resolve("artifact.json"),
                "{\"title\":\"选题\",\"items\":[{\"id\":\"tp-1\"}]}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("view.json"),
                "{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        ParsedGenerationOutput out = reader.read(root, "view.json", "artifact.json");

        assertEquals(2, ((Number) out.getRawView().get("version")).intValue());
        assertEquals("html", out.getRawView().get("format"));
        assertEquals("选题", out.getBusinessPayload().get("title"));
    }

    @Test
    void missing_sibling_artifact_throws() throws Exception {
        Files.write(root.resolve("view.json"),
                "{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "view.json", "artifact.json"));

        assertEquals("output file missing: artifact.json", ex.getMessage());
    }

    @Test
    void missing_view_file_throws() {
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "view.json", "artifact.json"));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void empty_view_file_throws() throws Exception {
        Files.write(root.resolve("view.json"), new byte[0]);
        Files.write(root.resolve("artifact.json"), "{}".getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "view.json", "artifact.json"));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void rejects_parent_segments() throws Exception {
        WorkspaceOutputReader reader = new WorkspaceOutputReader();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(root, "../view.json", "artifact.json"));

        assertTrue(ex.getMessage().contains("path escapes"));
    }

    @Test
    void declared_paths_missing_on_disk_do_not_fall_through() {
        WorkspaceOutputParser parser = parser(skill("demo", "view.json", "artifact.json", null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(ctx("demo", root)));

        assertEquals("output file missing: view.json", ex.getMessage());
    }

    @Test
    void parser_reads_catalog_paths() throws Exception {
        Files.createDirectories(root.resolve("out"));
        Files.write(root.resolve("out/view.json"),
                "{\"version\":1,\"title\":\"from-disk\",\"status\":\"ready\",\"blocks\":[]}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("out/artifact.json"),
                "{\"title\":\"from-disk\"}".getBytes(StandardCharsets.UTF_8));
        WorkspaceOutputParser parser = parser(skill("demo", "out/view.json", "out/artifact.json", null));

        ParsedGenerationOutput out = parser.parse(OutputParseContext.builder()
                .skillId("demo")
                .finalResponse("{\"output\":\"ignored.json\",\"view\":{\"title\":\"from-text\"}}")
                .workspaceRoot(root)
                .build());

        assertEquals("from-disk", out.getRawView().get("title"));
        assertEquals("from-disk", out.getBusinessPayload().get("title"));
    }

    @Test
    void configuration_registers_parser() {
        WorkspaceOutputParser bean = new ViewToolsConfiguration()
                .workspaceOutputParser(new InMemorySkillCatalog());

        assertNotNull(bean);
    }

    private static WorkspaceOutputParser parser(Skill skill) {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(skill);
        return new WorkspaceOutputParser(catalog);
    }

    private static Skill skill(String id, String view, String artifact, String planView) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef("classpath:" + id + ".md")
                .viewPath(view)
                .artifactPath(artifact)
                .planViewPath(planView)
                .build();
    }

    private static OutputParseContext ctx(String skillId, Path workspace) {
        return OutputParseContext.builder()
                .skillId(skillId)
                .workspaceRoot(workspace)
                .finalResponse("hello")
                .build();
    }
}

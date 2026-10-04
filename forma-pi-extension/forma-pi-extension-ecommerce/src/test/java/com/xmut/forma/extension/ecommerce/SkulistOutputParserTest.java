package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.extension.config.SkuToolsConfiguration;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkulistOutputParserTest {

    private static final String SKILL = "ecommerce-skulist";

    @TempDir
    Path root;

    @Test
    void order_is_highest_precedence() {
        Order order = SkulistOutputParser.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }

    @Test
    void applies_only_to_skulist() {
        SkulistOutputParser parser = parser();

        assertTrue(parser.appliesTo(ctx(SKILL, null)));
        assertFalse(parser.appliesTo(ctx("ecommerce-picklist", null)));
        assertFalse(parser.appliesTo(ctx(null, null)));
        assertFalse(parser.appliesTo(null));
    }

    @Test
    void plan_phase_reads_plan_slots() throws Exception {
        write("plan/view.json",
                "{\"version\":2,\"title\":\"策划\",\"format\":\"html\",\"content\":\"<p>p</p>\"}");
        write("plan/artifact.json", "{\"title\":\"策划\"}");
        SkulistOutputParser parser = parser();

        ParsedGenerationOutput out = parser.parse(ctx(SKILL, null));

        assertEquals("策划", out.getBusinessPayload().get("title"));
        assertEquals("html", out.getRawView().get("format"));
    }

    @Test
    void confirm_execute_reads_exec_slots() throws Exception {
        write("exec/view.json",
                "{\"version\":2,\"title\":\"执行\",\"format\":\"html\",\"content\":\"<p>e</p>\"}");
        write("exec/artifact.json", "{\"title\":\"执行\"}");
        write("plan/view.json",
                "{\"version\":2,\"title\":\"策划\",\"format\":\"html\",\"content\":\"<p>p</p>\"}");
        write("plan/artifact.json", "{\"title\":\"策划\"}");
        SkulistOutputParser parser = parser();

        ParsedGenerationOutput out = parser.parse(ctx(SKILL, "confirm_execute"));

        assertEquals("执行", out.getBusinessPayload().get("title"));
    }

    @Test
    void other_resume_option_stays_on_plan() throws Exception {
        write("plan/view.json",
                "{\"version\":2,\"title\":\"策划\",\"format\":\"html\",\"content\":\"<p>p</p>\"}");
        write("plan/artifact.json", "{\"title\":\"策划\"}");
        write("exec/view.json",
                "{\"version\":2,\"title\":\"执行\",\"format\":\"html\",\"content\":\"<p>e</p>\"}");
        write("exec/artifact.json", "{\"title\":\"执行\"}");
        SkulistOutputParser parser = parser();

        ParsedGenerationOutput out = parser.parse(ctx(SKILL, "supplement"));

        assertEquals("策划", out.getBusinessPayload().get("title"));
    }

    @Test
    void missing_plan_file_throws() {
        SkulistOutputParser parser = parser();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(ctx(SKILL, null)));

        assertEquals("output file missing: plan/view.json", ex.getMessage());
    }

    @Test
    void configuration_registers_parser() {
        SkulistOutputParser bean = new SkuToolsConfiguration()
                .skulistOutputParser(new InMemorySkillCatalog());

        assertNotNull(bean);
    }

    private SkulistOutputParser parser() {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(Skill.builder()
                .id(SKILL)
                .description(SKILL)
                .promptRef("classpath:skulist.md")
                .viewPath("exec/view.json")
                .artifactPath("exec/artifact.json")
                .planViewPath("plan/view.json")
                .planArtifactPath("plan/artifact.json")
                .build());
        return new SkulistOutputParser(catalog);
    }

    private OutputParseContext ctx(String skillId, String resumeOptionId) {
        return OutputParseContext.builder()
                .skillId(skillId)
                .resumeOptionId(resumeOptionId)
                .finalResponse("{\"output\":\"ignored.json\"}")
                .workspaceRoot(root)
                .build();
    }

    private void write(String rel, String body) throws Exception {
        Path path = root.resolve(rel);
        Files.createDirectories(path.getParent());
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }
}

package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.tool.ToolLevel;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InMemorySkillConfigTest {

    private static SkillManifest valid(String id, String version) {
        return SkillManifest.builder()
                .id(id)
                .version(version)
                .skillsPrompt("prompt for " + id)
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build();
    }

    @Test
    void register_get_resolve_list_happy_path() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        SkillManifest m = valid("certificate.ocr", "1.0.0");
        registry.register(m);

        assertThat(registry.get("certificate.ocr", "1.0.0")).contains(m);
        assertThat(registry.resolve("certificate.ocr")).isPresent();
        assertThat(registry.resolve("certificate.ocr").get().getSkillsPrompt())
                .contains("prompt for certificate.ocr");
        assertThat(registry.manifests()).hasSize(1);
    }

    @Test
    void resolve_tracks_latest_registered_as_current_pointer() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(valid("nav", "1.0.0"));
        registry.register(valid("nav", "1.1.0"));

        assertThat(registry.resolve("nav").get().getVersion()).isEqualTo("1.1.0");
        assertThat(registry.get("nav", "1.0.0")).isPresent();
        assertThat(registry.manifests()).hasSize(2);
    }

    @Test
    void duplicate_id_version_refused() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(valid("a", "1.0.0"));
        assertThatThrownBy(() -> registry.register(valid("a", "1.0.0")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("duplicate");
        assertThat(registry.manifests()).hasSize(1);
    }

    @Test
    void replace_allowed_when_mutation_on() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(valid("a", "1.0.0"));
        SkillManifest replaced = valid("a", "1.0.0").toBuilder()
                .skillsPrompt("replaced-prompt")
                .build();
        registry.replace(replaced);
        assertThat(registry.resolve("a").get().getSkillsPrompt()).isEqualTo("replaced-prompt");
    }

    @Test
    void mutation_false_runtime_register_fails_bootstrap_ok() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        assertThat(registry.properties().isAllowRuntimeMutation()).isFalse();

        assertThatThrownBy(() -> registry.register(valid("certificate.ocr", "1.0.0")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("runtime skill mutation disabled");

        registry.registerBootstrap(valid("certificate.ocr", "1.0.0"));
        assertThat(registry.resolve("certificate.ocr")).isPresent();

        registry.sealBootstrap();
        assertThatThrownBy(() -> registry.registerBootstrap(valid("other", "1.0.0")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("bootstrap window closed");

        assertThatThrownBy(() -> registry.unregister("certificate.ocr", "1.0.0"))
                .isInstanceOf(SkillValidationException.class);
        assertThatThrownBy(() -> registry.replace(valid("certificate.ocr", "1.0.0")))
                .isInstanceOf(SkillValidationException.class);
    }

    @Test
    void validation_rejects_null_tool_whitelist() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        assertThatThrownBy(() -> registry.register(SkillManifest.builder()
                .id("ok")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(null)
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("toolWhitelist");
    }

    @Test
    void validation_rejects_missing_and_illegal_fields() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());

        assertThatThrownBy(() -> registry.register(valid(null, "1.0.0")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("id");

        assertThatThrownBy(() -> registry.register(valid("bad id", "1.0.0")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("whitespace");

        assertThatThrownBy(() -> registry.register(valid("ok", "  ")))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("version");

        assertThatThrownBy(() -> registry.register(SkillManifest.builder()
                .id("ok")
                .version("1.0.0")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("skillsPrompt or promptRef");

        assertThatThrownBy(() -> registry.register(SkillManifest.builder()
                .id("ok")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(Arrays.asList("a", "  "))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("toolWhitelist");

        assertThatThrownBy(() -> registry.register(SkillManifest.builder()
                .id("ok")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.FORBIDDEN)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("FORBIDDEN");

        assertThatThrownBy(() -> registry.register(SkillManifest.builder()
                .id("ok")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(null)
                .build()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("graphTopology");

        assertThat(registry.manifests()).isEmpty();
    }

    @Test
    void sample_json_registers_and_resolves() throws Exception {
        SkillManifest sample = loadSampleCertificateOcr();
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        registry.registerBootstrap(sample);

        assertThat(registry.resolve("certificate.ocr")).isPresent();
        assertThat(registry.resolve("certificate.ocr").get().getGraphTopology())
                .isEqualTo(SkillGraphTopology.SIMPLE_AGENT_END);
        assertThat(registry.resolve("certificate.ocr").get().getToolWhitelist())
                .containsExactly("read_skill");
        assertThat(registry.resolve("certificate.ocr").get().getPromptRef())
                .isEqualTo("classpath:skills/certificate-ocr.md");
        assertThat(registry.resolve("certificate.ocr").get().getModelUseCase())
                .isEqualTo("certificate-ocr");
        assertThat(ActiveSkill.of(sample).text()).isNull();
    }

    @Test
    void test_standard_schema_json_registers_and_resolves() throws Exception {
        SkillManifest sample = loadSampleTestStandardSchema();
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        registry.registerBootstrap(sample);

        assertThat(registry.resolve("test-standard-schema")).isPresent();
        assertThat(registry.resolve("test-standard-schema").get().getGraphTopology())
                .isEqualTo(SkillGraphTopology.SIMPLE_AGENT_END);
        assertThat(registry.resolve("test-standard-schema").get().getToolWhitelist())
                .containsExactly("read_skill");
        assertThat(registry.resolve("test-standard-schema").get().getPromptRef())
                .isEqualTo("classpath:skills/test-standard-schema.md");
        assertThat(registry.resolve("test-standard-schema").get().getModelUseCase())
                .isEqualTo("test-standard-schema");
    }

    @Test
    void test_method_curve_schema_json_registers_and_resolves() throws Exception {
        SkillManifest sample = loadSampleTestMethodCurveSchema();
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        registry.registerBootstrap(sample);

        assertThat(registry.resolve("test-method-curve-schema")).isPresent();
        assertThat(registry.resolve("test-method-curve-schema").get().getGraphTopology())
                .isEqualTo(SkillGraphTopology.SIMPLE_AGENT_END);
        assertThat(registry.resolve("test-method-curve-schema").get().getToolWhitelist())
                .containsExactly("read_skill");
        assertThat(registry.resolve("test-method-curve-schema").get().getPromptRef())
                .isEqualTo("classpath:skills/test-method-curve-schema.md");
        assertThat(registry.resolve("test-method-curve-schema").get().getModelUseCase())
                .isEqualTo("test-method-curve-schema");
    }

    @Test
    void walk_in_paper_import_json_registers_and_resolves() throws Exception {
        SkillManifest sample = loadSampleWalkInImport();
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        registry.registerBootstrap(sample);

        assertThat(registry.resolve("walk-in-import")).isPresent();
        assertThat(registry.resolve("walk-in-import").get().getGraphTopology())
                .isEqualTo(SkillGraphTopology.SIMPLE_AGENT_END);
        assertThat(registry.resolve("walk-in-import").get().getToolWhitelist())
                .containsExactly("read_skill");
        assertThat(registry.resolve("walk-in-import").get().getPromptRef())
                .isEqualTo("classpath:skills/walk-in-import.md");
        assertThat(registry.resolve("walk-in-import").get().getModelUseCase())
                .isEqualTo("walk-in-import");
        assertThat(registry.resolve("walk-in-import").get().getMaxToolLevel())
                .isEqualTo(ToolLevel.READ);
    }

    @Test
    void inst_pdf_extract_json_registers_and_resolves() throws Exception {
        SkillManifest sample = loadSampleInstPdfExtract();
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        registry.registerBootstrap(sample);

        assertThat(registry.resolve("inst-pdf.extract")).isPresent();
        assertThat(registry.resolve("inst-pdf.extract").get().getGraphTopology())
                .isEqualTo(SkillGraphTopology.SIMPLE_AGENT_END);
        assertThat(registry.resolve("inst-pdf.extract").get().getToolWhitelist())
                .containsExactly("read_skill");
        assertThat(registry.resolve("inst-pdf.extract").get().getPromptRef())
                .isEqualTo("classpath:skills/inst-pdf-extract.md");
        assertThat(registry.resolve("inst-pdf.extract").get().getModelUseCase())
                .isEqualTo("inst-pdf");
        assertThat(registry.resolve("inst-pdf.extract").get().getMaxToolLevel())
                .isEqualTo(ToolLevel.READ);
    }

    @Test
    void no_skill_router_class_in_hermes() {
        assertThatThrownBy(() -> Class.forName("com.xmut.lims.pi.agent.skill.SkillRouter"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.xmut.lims.pi.agent.agent.SkillRouter"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    public static SkillManifest loadSampleCertificateOcr() throws Exception {
        try (InputStream in = InMemorySkillConfigTest.class.getResourceAsStream(
                "/skills/certificate-ocr.skill.json")) {
            assertThat(in).as("sample JSON on classpath").isNotNull();
            return SkillManifestJsonLoader.load(in);
        }
    }

    public static SkillManifest loadSampleTestStandardSchema() throws Exception {
        try (InputStream in = InMemorySkillConfigTest.class.getResourceAsStream(
                "/skills/test-standard-schema.skill.json")) {
            assertThat(in).as("test-standard-schema JSON on classpath").isNotNull();
            return SkillManifestJsonLoader.load(in);
        }
    }

    public static SkillManifest loadSampleTestMethodCurveSchema() throws Exception {
        try (InputStream in = InMemorySkillConfigTest.class.getResourceAsStream(
                "/skills/test-method-curve-schema.skill.json")) {
            assertThat(in).as("test-method-curve-schema JSON on classpath").isNotNull();
            return SkillManifestJsonLoader.load(in);
        }
    }

    public static SkillManifest loadSampleWalkInImport() throws Exception {
        try (InputStream in = InMemorySkillConfigTest.class.getResourceAsStream(
                "/skills/walk-in-import.skill.json")) {
            assertThat(in).as("walk-in-import JSON on classpath").isNotNull();
            return SkillManifestJsonLoader.load(in);
        }
    }

    public static SkillManifest loadSampleInstPdfExtract() throws Exception {
        try (InputStream in = InMemorySkillConfigTest.class.getResourceAsStream(
                "/skills/inst-pdf-extract.skill.json")) {
            assertThat(in).as("inst-pdf-extract JSON on classpath").isNotNull();
            return SkillManifestJsonLoader.load(in);
        }
    }
}

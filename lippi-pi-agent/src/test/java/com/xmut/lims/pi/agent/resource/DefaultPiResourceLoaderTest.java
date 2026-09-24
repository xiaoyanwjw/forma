package com.xmut.lims.pi.agent.resource;

import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPiResourceLoaderTest {

    @Test
    void scan_reads_echo_md_and_strips_frontmatter() {
        DefaultPiResourceLoader loader = testLoader();
        assertThat(loader.findPrompt("echo")).isPresent();
        PromptTemplate echo = loader.findPrompt("echo").get();
        assertThat(echo.getName()).isEqualTo("echo");
        assertThat(echo.getDescription()).isEqualTo("Echo remaining slash args");
        assertThat(echo.getBody()).isEqualTo("echo: $@");
        assertThat(loader.snapshot().getPrompts()).containsKey("echo");
    }

    @Test
    void expand_dollar_at_substitutes_remaining_args() {
        DefaultPiResourceLoader loader = testLoader();
        SlashExpansion expanded = loader.expandSlash("/echo hello world");
        assertThat(expanded.isExpanded()).isTrue();
        assertThat(expanded.getText()).isEqualTo("echo: hello world");
        assertThat(expanded.getSkillId()).isNull();
    }

    @Test
    void expand_dollar_at_empty_args_is_empty_string() {
        DefaultPiResourceLoader loader = testLoader();
        SlashExpansion expanded = loader.expandSlash("/echo");
        assertThat(expanded.getText()).isEqualTo("echo: ");
    }

    @Test
    void unknown_slash_stays_plain_user_text() {
        DefaultPiResourceLoader loader = testLoader();
        SlashExpansion expanded = loader.expandSlash("/unknown-cmd foo");
        assertThat(expanded.isExpanded()).isFalse();
        assertThat(expanded.getText()).isEqualTo("/unknown-cmd foo");
        assertThat(expanded.getSkillId()).isNull();
    }

    @Test
    void missing_prompt_file_does_not_throw() {
        assertThat(ClasspathPromptBootstrap.load(
                new PathMatchingResourcePatternResolver(),
                "classpath*:prompts/does-not-exist-*.md")).isEmpty();
        DefaultPiResourceLoader loader = testLoader();
        assertThat(loader.findPrompt("missing")).isEmpty();
    }

    @Test
    void reload_rescans_prompts() {
        DefaultPiResourceLoader loader = testLoader();
        loader.reload();
        assertThat(loader.findPrompt("echo")).isPresent();
    }

    @Test
    void expand_skill_colon_loads_body_and_sets_skill_id() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .skillsPrompt("inline-ocr-policy")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(java.util.Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        SlashExpansion expanded = loader.expandSlash("/skill:certificate.ocr");
        assertThat(expanded.isExpanded()).isTrue();
        assertThat(expanded.getSkillId()).isEqualTo("certificate.ocr");
        assertThat(expanded.getText()).contains("资质证书 OCR");
        assertThat(expanded.getText()).doesNotContain("inline-ocr-policy");
    }

    @Test
    void expand_skill_test_standard_schema_loads_body_and_sets_skill_id() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("test-standard-schema")
                .version("1.0.0")
                .promptRef("classpath:skills/test-standard-schema.md")
                .toolWhitelist(java.util.Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        SlashExpansion expanded = loader.expandSlash("/skill:test-standard-schema");
        assertThat(expanded.isExpanded()).isTrue();
        assertThat(expanded.getSkillId()).isEqualTo("test-standard-schema");
        assertThat(expanded.getText()).contains("ParamSchemeRoot");
        assertThat(expanded.getText()).contains("lims_testing_runs");
        assertThat(expanded.getText()).contains("noQcBindings");
    }

    @Test
    void expand_skill_walk_in_paper_import_loads_body_and_sets_skill_id() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("walk-in-import")
                .version("1.0.0")
                .promptRef("classpath:skills/walk-in-import.md")
                .toolWhitelist(java.util.Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        SlashExpansion expanded = loader.expandSlash("/skill:walk-in-import");
        assertThat(expanded.isExpanded()).isTrue();
        assertThat(expanded.getSkillId()).isEqualTo("walk-in-import");
        assertThat(expanded.getText()).contains("rows");
        assertThat(expanded.getText()).contains("categoryNo");
        assertThat(expanded.getText()).contains("itemCode");
        assertThat(expanded.getText()).contains("standardCode");
    }

    @Test
    void expand_skill_does_not_fallback_when_prompt_ref_missing() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("broken.skill")
                .version("1.0.0")
                .skillsPrompt("INLINE-MUST-NOT-WIN")
                .promptRef("classpath:skills/does-not-exist.md")
                .toolWhitelist(java.util.Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        SlashExpansion expanded = loader.expandSlash("/skill:broken.skill");
        assertThat(expanded.isExpanded()).isFalse();
        assertThat(expanded.getText()).isEqualTo("/skill:broken.skill");
        assertThat(expanded.getText()).doesNotContain("INLINE-MUST-NOT-WIN");
    }

    @Test
    void snapshot_delegates_skill_and_tool_ids_without_second_registry() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("demo.skill")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(java.util.Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        AgentResourceSnapshot snap = loader.snapshot();
        assertThat(snap.getSkillIds()).contains("demo.skill");
        assertThat(snap.getExtensionNames()).isEmpty();
    }

    private static DefaultPiResourceLoader testLoader() {
        return new DefaultPiResourceLoader();
    }
}

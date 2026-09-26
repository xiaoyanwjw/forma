package com.xmut.lims.pi.agent.resource;

import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.agent.skill.Skill;
import com.xmut.lims.pi.agent.skill.Skills;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
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
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("ecommerce-picklist")
                .description("inline-should-not-win")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(java.util.Collections.singletonList("read_skill"))
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(), skills, InMemoryToolCatalog.empty(),
                java.util.Collections.emptyList());
        SlashExpansion expanded = loader.expandSlash("/skill:ecommerce-picklist");
        assertThat(expanded.isExpanded()).isTrue();
        assertThat(expanded.getSkillId()).isEqualTo("ecommerce-picklist");
        assertThat(expanded.getText()).contains("Picklist rules for Adam.");
        assertThat(expanded.getText()).doesNotContain("inline-should-not-win");
    }

    @Test
    void expand_skill_does_not_fallback_when_prompt_ref_missing() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("broken.skill")
                .description("INLINE-MUST-NOT-WIN")
                .promptRef("classpath:skills/does-not-exist.md")
                .allowedTools(java.util.Collections.emptyList())
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(new PathMatchingResourcePatternResolver(), skills, InMemoryToolCatalog.empty(), java.util.Collections.emptyList());
        SlashExpansion expanded = loader.expandSlash("/skill:broken.skill");
        assertThat(expanded.isExpanded()).isFalse();
        assertThat(expanded.getText()).isEqualTo("/skill:broken.skill");
        assertThat(expanded.getText()).doesNotContain("INLINE-MUST-NOT-WIN");
    }

    @Test
    void snapshotIncludesSceneSkillsFromSkillMd() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(new SkillCatalogProperties(false));
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Skills.loadFromClasspath(skills, resolver, "classpath*:scenes/*/*/SKILL.md");
        skills.sealBootstrap();
        assertThat(skills.resolve("ecommerce-picklist")).isPresent();
        assertThat(skills.listByScene("ecommerce"))
                .extracting(Skill::getId)
                .contains("ecommerce-picklist", "ecommerce-skulist");
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                resolver, skills, InMemoryToolCatalog.empty(), java.util.Collections.emptyList());
        assertThat(loader.snapshot().getSkillIds())
                .contains("ecommerce-picklist", "ecommerce-skulist");
    }

    @Test
    void snapshot_delegates_skill_and_tool_ids_without_second_registry() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("demo.skill")
                .description("p")
                .promptRef("classpath:skills/demo.md")
                .allowedTools(java.util.Collections.emptyList())
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(new PathMatchingResourcePatternResolver(), skills, InMemoryToolCatalog.empty(), java.util.Collections.emptyList());
        AgentResourceSnapshot snap = loader.snapshot();
        assertThat(snap.getSkillIds()).contains("demo.skill");
        assertThat(snap.getExtensionNames()).isEmpty();
    }

    private static DefaultPiResourceLoader testLoader() {
        return new DefaultPiResourceLoader();
    }
}

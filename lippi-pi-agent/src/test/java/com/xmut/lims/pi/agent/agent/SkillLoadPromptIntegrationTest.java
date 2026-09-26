package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.Skill;
import com.xmut.lims.pi.agent.skill.Skills;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import org.springframework.core.io.ClassPathResource;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.Tool;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.agent.ContextCompressor;

/**
 * SkillCatalog → Loop.input → SKILLS 目录 / AVAILABLE_TOOLS → PromptBuilder。
 * Skill 全文经 read_skill，不进 Stable。
 */
class SkillLoadPromptIntegrationTest {

    @Test
    void resolve_skill_writes_catalog_not_body_into_system_prompt() throws Exception {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        Skill sample = Skills.parse(
                new ClassPathResource("scenes/ecommerce/ecommerce-picklist/SKILL.md"), "ecommerce");
        registry.registerBootstrap(sample);

        AtomicReference<List<Message>> captured = new AtomicReference<>();
        AtomicReference<List<ToolSchema>> capturedTools = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request.getMessages());
            capturedTools.set(request.getTools());
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };

        InMemoryToolCatalog tools = InMemoryToolCatalog.ofBindings(Collections.singletonList(
                ToolBinding.of(ToolDefinition.builder()
                        .id("read_skill")
                        .text("[read_skill]")
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(), (call, ctx) -> null)));

        DefaultPromptBuilder promptBuilder = new DefaultPromptBuilder();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, promptBuilder, tools, ContextCompressor.NOOP),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), tools,
                registry);

        ConversationResult result = loop.run(TurnInput.withUser("picklist please")
                .skillId("ecommerce-picklist")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(captured.get()).isNotNull();
        String system = captured.get().get(0).getContent();
        assertThat(system).contains("ecommerce-picklist");
        assertThat(system).contains("read_skill");
        assertThat(system).doesNotContain("非实时平台数据");
        assertThat(capturedTools.get()).extracting(ToolSchema::getName).containsExactly("read_skill");
        assertThat(sample.getPromptRef()).contains("scenes/ecommerce/ecommerce-picklist/SKILL.md");
    }

    @Test
    void domain_fallback_resolves_when_skillId_blank() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(Skill.builder()
                .id("lims.nav")
                .description("NAV-SKILL-MARKER")
                .promptRef("classpath:skills/lims-nav.md")
                .allowedTools(Collections.emptyList())
                .build());

    }

    @Test
    void skillId_takes_priority_over_domain() {
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(validSkill("a", "A-PROMPT"));
        registry.register(validSkill("b", "B-PROMPT"));

    }

    @Test
    void tools_text_lands_in_stable_tools_slot() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("alpha", "ALPHA-TEXT", (call, ctx) -> null)));

    }

    @Test
    void skills_and_tools_text_are_symmetric_with_available_lists() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("t", "TOOL-STABLE", (call, ctx) -> null)));
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(validSkill("s", "SKILL-STABLE").toBuilder()
                .allowedTools(Collections.singletonList("t"))
                .build());
    }

    @Test
    void whitelist_intersects_policy_schemas() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Arrays.asList(
                ToolTestSupport.tool("alpha", (call, ctx) -> null),
                ToolTestSupport.tool("beta", (call, ctx) -> null),
                ToolTestSupport.tool("gamma", (call, ctx) -> null)));

        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(Skill.builder()
                .id("s1")
                .description("S1")
                .promptRef("classpath:skills/s1.md")
                .allowedTools(Arrays.asList("alpha", "gamma", "unknown_tool"))
                .build());

    }

    @Test
    void empty_whitelist_omits_available_tools_even_if_policy_has_tools() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                ToolTestSupport.tool("alpha", (call, ctx) -> null)));
        InMemorySkillCatalog registry = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        registry.register(Skill.builder()
                .id("empty-tools")
                .description("E")
                .promptRef("classpath:skills/empty-tools.md")
                .allowedTools(Collections.emptyList())
                .build());
    }

    @Test
    void unknown_skillId_fails_closed_even_without_registry() {
        InMemoryToolCatalog policy = new InMemoryToolCatalog(Collections.singletonList(
                new Tool("t1", ToolSchema.builder().name("t1").build(),
                        (call, ctx) -> null)));

        InMemorySkillCatalog empty = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
    }

    private static Skill validSkill(String id, String prompt) {
        return Skill.builder()
                .id(id)
                .description(prompt)
                .promptRef("classpath:skills/" + id + ".md")
                .allowedTools(Collections.emptyList())
                .build();
    }
}

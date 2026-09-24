package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfigTest;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import com.xmut.lims.pi.agent.tool.ToolRegistration;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SkillConfig → Loop.input → SKILLS 目录 / AVAILABLE_TOOLS → PromptBuilder。
 * Skill 全文经 read_skill，不进 Stable。
 */
class SkillLoadPromptIntegrationTest {

    @Test
    void resolve_skill_writes_catalog_not_body_into_system_prompt() throws Exception {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.defaults());
        SkillManifest sample = InMemorySkillConfigTest.loadSampleCertificateOcr();
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

        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Collections.singletonList(
                ToolBinding.of(ToolManifest.builder()
                        .id("read_skill")
                        .text("[read_skill]")
                        .level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(), (call, ctx) -> null)));

        DefaultPromptBuilder promptBuilder = new DefaultPromptBuilder();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, promptBuilder, tools),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                null,
                tools,
                registry);

        ConversationResult result = loop.run(TurnInput.withUser("ocr please")
                .skillId("certificate.ocr")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(captured.get()).isNotNull();
        String system = captured.get().get(0).getContent();
        assertThat(system).contains("certificate.ocr");
        assertThat(system).contains("read_skill");
        assertThat(system).doesNotContain("certType");
        assertThat(capturedTools.get()).extracting(ToolSchema::getName).containsExactly("read_skill");
        assertThat(sample.getModelUseCase()).isEqualTo("certificate-ocr");
    }

    @Test
    void domain_fallback_resolves_when_skillId_blank() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(SkillManifest.builder()
                .id("lims.nav")
                .version("0.1.0")
                .displayName("Nav")
                .skillsPrompt("NAV-SKILL-MARKER")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());

    }

    @Test
    void skillId_takes_priority_over_domain() {
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(validSkill("a", "A-PROMPT"));
        registry.register(validSkill("b", "B-PROMPT"));

    }

    @Test
    void tools_text_lands_in_stable_tools_slot() {
        DefaultToolConfig policy = new DefaultToolConfig(Collections.singletonList(
                ToolTestSupport.registration("alpha", ToolLevel.READ, "ALPHA-TEXT", (call, ctx) -> null)));

    }

    @Test
    void skills_and_tools_text_are_symmetric_with_available_lists() {
        DefaultToolConfig policy = new DefaultToolConfig(Collections.singletonList(
                ToolTestSupport.registration("t", ToolLevel.READ, "TOOL-STABLE", (call, ctx) -> null)));
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(validSkill("s", "SKILL-STABLE").toBuilder()
                .toolWhitelist(Collections.singletonList("t"))
                .build());
    }

    @Test
    void whitelist_intersects_policy_schemas() {
        DefaultToolConfig policy = new DefaultToolConfig(Arrays.asList(
                ToolTestSupport.registration("alpha", ToolLevel.READ, (call, ctx) -> null),
                ToolTestSupport.registration("beta", ToolLevel.READ, (call, ctx) -> null),
                ToolTestSupport.registration("gamma", ToolLevel.READ, (call, ctx) -> null)));

        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(SkillManifest.builder()
                .id("s1")
                .version("1.0.0")
                .skillsPrompt("S1")
                .toolWhitelist(Arrays.asList("alpha", "gamma", "unknown_tool"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());

    }

    @Test
    void empty_whitelist_omits_available_tools_even_if_policy_has_tools() {
        DefaultToolConfig policy = new DefaultToolConfig(Collections.singletonList(
                ToolTestSupport.registration("alpha", ToolLevel.READ, (call, ctx) -> null)));
        InMemorySkillConfig registry = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        registry.register(SkillManifest.builder()
                .id("empty-tools")
                .version("1.0.0")
                .skillsPrompt("E")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());
    }

    @Test
    void unknown_skillId_fails_closed_even_without_registry() {
        DefaultToolConfig policy = new DefaultToolConfig(Collections.singletonList(
                new ToolRegistration("t1", ToolSchema.builder().name("t1").build(),
                        ToolLevel.READ, (call, ctx) -> null)));

        InMemorySkillConfig empty = new InMemorySkillConfig(SkillConfigProperties.defaults());
    }

    private static SkillManifest validSkill(String id, String prompt) {
        return SkillManifest.builder()
                .id(id)
                .version("1.0.0")
                .skillsPrompt(prompt)
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build();
    }
}

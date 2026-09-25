package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.agent.ContextCompressor;

/**
 * certificate.ocr：目录进 Stable + 首轮 read_skill 拉 md + 次轮出 JSON。
 */
class CertificateOcrPiIntegrationTest {

    @Test
    void two_turn_read_skill_then_ocr_json() {
        InMemorySkillConfig skills = new InMemorySkillConfig(new SkillConfigProperties(true));
        skills.registerBootstrap(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .displayName("Certificate OCR")
                .description("Extract fields")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .modelUseCase(InMemoryModelCatalog.CERTIFICATE_OCR_USE_CASE)
                .build());
        skills.sealBootstrap();

        ToolBinding readSkill = ToolBinding.of(
                ToolManifest.builder()
                        .id(ReadSkill.TOOL_ID)
                        .text("[read_skill]")
                        .level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(),
                new ReadSkill(skills));
        DefaultToolConfig toolConfig = DefaultToolConfig.ofBindings(Collections.singletonList(readSkill));

        AtomicInteger turns = new AtomicInteger();
        AtomicReference<String> capturedUseCase = new AtomicReference<>();
        AtomicReference<Boolean> sawImage = new AtomicReference<>(false);
        AtomicReference<List<ToolSchema>> toolsOnFirst = new AtomicReference<>();

        ModelProvider fake = request -> {
            int n = turns.incrementAndGet();
            capturedUseCase.set(request.getUseCase());
            if (request.getMessages() != null) {
                for (Message m : request.getMessages()) {
                    if (m != null && m.hasImagePart()) {
                        sawImage.set(true);
                    }
                }
            }
            if (n == 1) {
                toolsOnFirst.set(request.getTools());
                ObjectNode args = JsonNodeFactory.instance.objectNode();
                args.put("skill_id", "certificate.ocr");
                return ModelResponse.builder()
                        .content(null)
                        .toolCalls(Collections.singletonList(
                                new ToolCallEntry("call-1", "read_skill", args)))
                        .finishReason("tool_calls")
                        .build();
            }
            return ModelResponse.builder()
                    .content("{\"fields\":[]}")
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), toolConfig, ContextCompressor.NOOP),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), toolConfig, skills);

        Message user = Message.user(Arrays.asList(
                ContentPart.text("extract"),
                ContentPart.imageUrl("https://example.com/cert.png", "high")));

        ConversationResult result = loop.run(TurnInput.builder()
                .skillId("certificate.ocr")
                .messages(Collections.singletonList(user))
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("{\"fields\":[]}");
        assertThat(turns.get()).isEqualTo(2);
        assertThat(capturedUseCase.get()).isEqualTo("certificate-ocr");
        assertThat(sawImage.get()).isTrue();
        assertThat(toolsOnFirst.get()).extracting(ToolSchema::getName).containsExactly("read_skill");
    }

    @Test
    void input_projects_catalog_and_read_skill_whitelist() {
        InMemorySkillConfig skills = new InMemorySkillConfig(new SkillConfigProperties(true));
        skills.register(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .description("Extract")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .modelUseCase("certificate-ocr")
                .build());

        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Collections.singletonList(
                ToolBinding.of(ToolManifest.builder()
                        .id("read_skill")
                        .text("[read_skill]")
                        .level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(), (c, ctx) -> null)));

    }

    @Test
    void unknown_skillId_fails_closed() {
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(request -> ModelResponse.builder()
                        .content("should-not-run")
                        .toolCalls(Collections.emptyList())
                        .build(), new DefaultPromptBuilder(), DefaultToolConfig.empty(), ContextCompressor.NOOP),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), DefaultToolConfig.empty(),
                new InMemorySkillConfig(SkillConfigProperties.defaults()));

        ConversationResult result = loop.run(TurnInput.builder()
                .skillId("nope.missing")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("hi")))
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.FAILED);
        assertThat(result.getFinalResponse()).contains("unknown skillId");
    }

    @Test
    void default_tool_loop_still_uses_hermes_default_without_skill() {
        AtomicReference<String> capturedUseCase = new AtomicReference<>();
        ModelProvider fake = request -> {
            capturedUseCase.set(request.getUseCase());
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), DefaultToolConfig.empty(), ContextCompressor.NOOP), new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25), DefaultToolConfig.empty(), null);

        ConversationResult result = loop.run(TurnInput.withUser("hello")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(capturedUseCase.get()).isEqualTo(InMemoryModelCatalog.DEFAULT_USE_CASE);
    }
}

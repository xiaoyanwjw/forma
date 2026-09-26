package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.agent.skill.Skills;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SKILL.md 装载后：目录进 Stable + 首轮 read_skill 拉正文 + 次轮出文本。
 */
class SkillsPiIntegrationTest {

    @Test
    void two_turn_read_skill_then_answer() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(new SkillCatalogProperties(true));
        Skills.loadFromClasspath(skills, new PathMatchingResourcePatternResolver(),
                Skills.DEFAULT_PATTERN);
        skills.sealBootstrap();

        ToolBinding readSkill = ToolBinding.of(
                ToolDefinition.builder()
                        .id(ReadSkill.TOOL_ID)
                        .text("[read_skill]")
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(),
                new ReadSkill(skills));
        InMemoryToolCatalog toolConfig = InMemoryToolCatalog.ofBindings(Collections.singletonList(readSkill));

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
                args.put("skill_id", "ecommerce-picklist");
                return ModelResponse.builder()
                        .content(null)
                        .toolCalls(Collections.singletonList(
                                new ToolCallEntry("call-1", "read_skill", args)))
                        .finishReason("tool_calls")
                        .build();
            }
            return ModelResponse.builder()
                    .content("{\"items\":[]}")
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        };

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), toolConfig, ContextCompressor.NOOP),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25),
                toolConfig, skills);

        Message user = Message.user(Arrays.asList(
                ContentPart.text("extract"),
                ContentPart.imageUrl("https://example.com/product.png", "high")));

        ConversationResult result = loop.run(TurnInput.builder()
                .skillId("ecommerce-picklist")
                .messages(Collections.singletonList(user))
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("{\"items\":[]}");
        assertThat(turns.get()).isEqualTo(2);
        assertThat(capturedUseCase.get()).isEqualTo(InMemoryModelCatalog.DEFAULT_USE_CASE);
        assertThat(sawImage.get()).isTrue();
        assertThat(toolsOnFirst.get()).extracting(ToolSchema::getName).containsExactly("read_skill");
    }

    @Test
    void unknown_skillId_fails_closed() {
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(request -> ModelResponse.builder()
                        .content("should-not-run")
                        .toolCalls(Collections.emptyList())
                        .build(), new DefaultPromptBuilder(), InMemoryToolCatalog.empty(), ContextCompressor.NOOP),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25),
                InMemoryToolCatalog.empty(),
                new InMemorySkillCatalog(SkillCatalogProperties.defaults()));

        ConversationResult result = loop.run(TurnInput.builder()
                .skillId("nope.missing")
                .messages(Collections.singletonList(Message.user("hi")))
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
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), InMemoryToolCatalog.empty(),
                        ContextCompressor.NOOP),
                new InMemoryCheckpointer(), new InMemoryResumeIdempotencyStore(), new IterationBudget(25),
                InMemoryToolCatalog.empty(), null);

        ConversationResult result = loop.run(TurnInput.withUser("hello").build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(capturedUseCase.get()).isEqualTo(InMemoryModelCatalog.DEFAULT_USE_CASE);
    }
}

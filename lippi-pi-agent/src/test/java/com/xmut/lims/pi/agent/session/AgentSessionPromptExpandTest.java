package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.extension.BeforeAgentStartResult;
import com.xmut.lims.pi.agent.extension.ExtensionRunner;
import com.xmut.lims.pi.agent.extension.PiExtension;
import com.xmut.lims.pi.agent.extension.SlashCommand;
import com.xmut.lims.pi.agent.extension.ToolPolicyExtension;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.agent.Agent;
import com.xmut.lims.pi.agent.agent.DefaultAgent;
import com.xmut.lims.pi.agent.agent.DefaultPromptBuilder;
import com.xmut.lims.pi.agent.agent.DefaultToolLoopGraph;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.resource.DefaultPiResourceLoader;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSessionPromptExpandTest {

    @Mock
    private Agent conversationLoop;

    private SessionStore sessionStore;

    @BeforeEach
    void stubStore() {
        sessionStore = new InMemorySessionStore();
    }

    @Test
    void command_short_circuit_does_not_call_loop() {
        PiExtension ping = bus -> bus.register(com.xmut.lims.pi.agent.event.PiEventType.COMMAND, e -> {
            PromptRequest req = (PromptRequest) e.getPayload();
            SlashCommand command = SlashCommand.parse(req != null ? req.getText() : null);
            if (command != null && "pingcmd".equals(command.getName())) {
                return TurnResult.ok("cmd-run", null, "pong-cmd", Collections.emptyList());
            }
            return null;
        });
        DefaultAgentSession session = sessionWith(runner(ping), testLoader());

        TurnResult result = session.prompt(PromptRequest.builder()
                .text("/pingcmd")
                .build());

        assertThat(result.getFinalResponse()).isEqualTo("pong-cmd");
        assertThat(result.getSessionId()).isNotBlank();
        verify(conversationLoop, never()).run(any(), any());
    }

    @Test
    void echo_slash_expands_into_user_message() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("/echo hello")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).getContent()).isEqualTo("echo: hello");
    }

    @Test
    void skill_slash_puts_body_in_user_not_overwriting_explicit_template_skill() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .skillsPrompt("inline-should-not-win")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                DefaultToolConfig.empty());
        DefaultAgentSession session = sessionWith(runner(), loader);

        session.prompt(PromptRequest.builder()
                .text("/skill:certificate.ocr")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).getContent()).contains("资质证书 OCR");
        assertThat(cap.getValue().getSkillId()).isEqualTo("certificate.ocr");
    }

    @Test
    void skill_slash_body_in_user_not_in_stable() {
        AtomicReference<List<Message>> captured = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request.getMessages());
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .skillsPrompt("inline-should-not-win")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Collections.singletonList(
                ToolBinding.of(ToolManifest.builder()
                        .id("read_skill")
                        .text("[read_skill]")
                        .level(ToolLevel.READ)
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(), (call, ctx) -> null)));
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), tools),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                null,
                tools,
                skills);
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(
                new PathMatchingResourcePatternResolver(),
                skills,
                tools);
        DefaultAgentSession session = new DefaultAgentSession(
                loop, new InMemorySessionStore(), loader, runner());

        session.prompt(PromptRequest.builder()
                .text("/skill:certificate.ocr")
                .build());

        assertThat(captured.get()).isNotNull();
        String system = captured.get().get(0).getContent();
        String user = captured.get().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .findFirst()
                .map(Message::getContent)
                .orElse("");
        assertThat(system).contains("certificate.ocr");
        assertThat(system).doesNotContain("certType");
        assertThat(system).doesNotContain("资质证书 OCR");
        assertThat(user).contains("资质证书 OCR");
        assertThat(user).contains("certType");
    }

    @Test
    void rewrite_replaces_user_when_whitespace_differs() {
        List<Message> rewritten = Session.rewriteUserHistory(
                Collections.singletonList(Message.user("/echo hello")),
                "echo: hello",
                "  /echo hello  ");
        assertThat(rewritten).hasSize(1);
        assertThat(rewritten.get(0).getContent()).isEqualTo("echo: hello");
    }

    @Test
    void echo_slash_with_padded_text_and_history_does_not_double() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("  /echo hello  ")
                .messages(Collections.singletonList(Message.user("/echo hello")))
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages())
                .extracting(Message::getContent)
                .containsExactly("echo: hello");
    }

    @Test
    void unknown_slash_stays_plain_text() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("/not-a-template")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).getContent())
                .isEqualTo("/not-a-template");
    }

    @Test
    void ocr_multimodal_without_slash_skips_expand() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        Message ocr = Message.user(Arrays.asList(
                ContentPart.text("extract"),
                ContentPart.imageUrl("https://img/cert.png")));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("extract")
                .skillId("certificate.ocr")
                .messages(Collections.singletonList(ocr))
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).hasImagePart()).isTrue();
        assertThat(cap.getValue().getSkillId()).isEqualTo("certificate.ocr");
    }

    @Test
    void before_agent_start_goes_to_volatile_append() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        PiExtension ext = bus -> bus.register(com.xmut.lims.pi.agent.event.PiEventType.BEFORE_AGENT_START, e ->
                BeforeAgentStartResult.variable("VOL-FROM-EXT"));
        DefaultAgentSession session = sessionWith(runner(ext), testLoader());

        session.prompt(PromptRequest.builder()
                .text("hello")
                .context("PAGE")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getBeforeAgentStart().getVariable()).isEqualTo("VOL-FROM-EXT");
        assertThat(cap.getValue().getContext()).isEqualTo("PAGE");
    }

    @Test
    void agent_end_called_on_ok_failed_and_resume() {
        AtomicInteger ends = new AtomicInteger();
        PiExtension ext = bus -> bus.subscribe(e -> {
            if (e.getType() == com.xmut.lims.pi.agent.event.PiEventType.AGENT_END) {
                ends.incrementAndGet();
            }
        });
        DefaultAgentSession session = sessionWith(runner(ext), testLoader());

        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()))
                .thenReturn(ConversationResult.failed("r2", "boom"));
        when(conversationLoop.resume(any(ResumeRequest.class), any()))
                .thenReturn(ConversationResult.suspended("r3", "human"));

        session.prompt(PromptRequest.builder().text("a").build());
        session.prompt(PromptRequest.builder().text("b").build());
        session.resume(ResumeRequest.builder()
                .runId("r3")
                .decision(com.xmut.lims.pi.agent.tool.ToolDecision.APPROVE)
                .build());

        assertThat(ends.get()).isEqualTo(3);
    }

    @Test
    void command_throw_is_swallowed_and_prompt_continues() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        PiExtension boom = bus -> bus.register(com.xmut.lims.pi.agent.event.PiEventType.COMMAND, e -> {
            throw new IllegalStateException("command boom");
        });
        DefaultAgentSession session = sessionWith(runner(boom), testLoader());

        TurnResult result = session.prompt(PromptRequest.builder()
                .text("/pingcmd")
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        verify(conversationLoop).run(any(), any());
    }

    @Test
    void before_agent_start_throw_is_swallowed_and_prompt_continues() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        PiExtension boom = bus -> bus.register(com.xmut.lims.pi.agent.event.PiEventType.BEFORE_AGENT_START, e -> {
            throw new IllegalStateException("start boom");
        });
        DefaultAgentSession session = sessionWith(runner(boom), testLoader());

        TurnResult result = session.prompt(PromptRequest.builder()
                .text("hello")
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        verify(conversationLoop).run(any(), any());
    }

    @Test
    void explicit_skill_id_not_overwritten_by_template() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("/echo x")
                .skillId("certificate.ocr")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getSkillId()).isEqualTo("certificate.ocr");
        assertThat(cap.getValue().getMessages().get(0).getContent()).isEqualTo("echo: x");
    }

    private DefaultAgentSession sessionWith(ExtensionRunner runner, DefaultPiResourceLoader loader) {
        return new DefaultAgentSession(conversationLoop, sessionStore, loader, runner);
    }

    private static ExtensionRunner runner(PiExtension... extras) {
        java.util.List<PiExtension> exts = new java.util.ArrayList<>();
        exts.add(new ToolPolicyExtension(DefaultToolConfig.empty()));
        if (extras != null) {
            Collections.addAll(exts, extras);
        }
        return new ExtensionRunner(exts);
    }

    private static DefaultPiResourceLoader testLoader() {
        return new DefaultPiResourceLoader();
    }
}

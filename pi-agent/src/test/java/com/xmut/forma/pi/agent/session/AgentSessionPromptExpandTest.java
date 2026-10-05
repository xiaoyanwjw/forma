package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.ConversationResult;
import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ExtensionRunner;
import com.xmut.forma.pi.agent.extension.PiExtension;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.agent.extension.SlashCommand;
import com.xmut.forma.pi.agent.extension.ToolPolicyExtension;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.forma.pi.agent.agent.Agent;
import com.xmut.forma.pi.agent.agent.DefaultAgent;
import com.xmut.forma.pi.agent.agent.DefaultPromptBuilder;
import com.xmut.forma.pi.agent.agent.DefaultToolLoopGraph;
import com.xmut.forma.pi.ai.message.ContentPart;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.model.ToolSchema;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.agent.resource.DefaultPiResourceLoader;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalogProperties;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.forma.pi.agent.tool.ToolBinding;
import com.xmut.forma.pi.agent.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.xmut.forma.pi.agent.IterationBudget;
import com.xmut.forma.pi.agent.agent.ContextCompressor;

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
        PiExtension ping = bus -> bus.register(com.xmut.forma.pi.agent.event.PiEventType.COMMAND, e -> {
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
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("ecommerce-picklist")
                .description("inline-should-not-win")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .build());
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(new PathMatchingResourcePatternResolver(), skills, InMemoryToolCatalog.empty(), java.util.Collections.emptyList());
        DefaultAgentSession session = sessionWith(runner(), loader);

        session.prompt(PromptRequest.builder()
                .text("/skill:ecommerce-picklist")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).getContent()).contains("非实时平台全站行情");
        assertThat(cap.getValue().getSkillId()).isEqualTo("ecommerce-picklist");
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
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("ecommerce-picklist")
                .description("inline-should-not-win")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .build());
        InMemoryToolCatalog tools = InMemoryToolCatalog.ofBindings(Collections.singletonList(
                ToolBinding.of(ToolDefinition.builder()
                        .id("read_skill")
                        .text("[read_skill]")
                        .schema(ToolSchema.builder().name("read_skill").build())
                        .build(), (call, ctx) -> null)));
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake, new DefaultPromptBuilder(), tools, ContextCompressor.NOOP),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(), new IterationBudget(25), tools,
                skills);
        DefaultPiResourceLoader loader = new DefaultPiResourceLoader(new PathMatchingResourcePatternResolver(), skills, tools, java.util.Collections.emptyList());
        com.xmut.forma.pi.agent.event.PiEventBus bus = new com.xmut.forma.pi.agent.event.DefaultPiEventBus();
        runner().register(bus);
        DefaultAgentSession session = new DefaultAgentSession(
                loop, new InMemorySessionStore(), loader, bus);

        session.prompt(PromptRequest.builder()
                .text("/skill:ecommerce-picklist")
                .build());

        assertThat(captured.get()).isNotNull();
        String system = captured.get().get(0).getContent();
        String user = captured.get().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .findFirst()
                .map(Message::getContent)
                .orElse("");
        assertThat(system).contains("ecommerce-picklist");
        assertThat(system).doesNotContain("非实时平台全站行情");
        assertThat(user).contains("非实时平台全站行情");
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
    void multimodal_without_slash_keeps_image_and_skill() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        Message ocr = Message.user(Arrays.asList(
                ContentPart.text("extract"),
                ContentPart.imageUrl("https://img/product.png")));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("extract")
                .skillId("ecommerce-picklist")
                .messages(Collections.singletonList(ocr))
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages().get(0).hasImagePart()).isTrue();
        assertThat(cap.getValue().getSkillId()).isEqualTo("ecommerce-picklist");
    }

    @Test
    void before_agent_start_goes_to_volatile_append() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        PiExtension ext = bus -> bus.register(com.xmut.forma.pi.agent.event.PiEventType.BEFORE_AGENT_START, (mod, e) ->
                mod.getSystem().appendVariable("VOL-FROM-EXT"));
        DefaultAgentSession session = sessionWith(runner(ext), testLoader());

        session.prompt(PromptRequest.builder()
                .text("hello")
                .context("PAGE")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getContextModifier().getAppend().getVariable()).isEqualTo("VOL-FROM-EXT");
        assertThat(cap.getValue().getContext()).isEqualTo("PAGE");
    }

    @Test
    void agent_end_called_on_ok_failed_and_resume() {
        AtomicInteger ends = new AtomicInteger();
        PiExtension ext = bus -> bus.subscribe(e -> {
            if (e.getType() == com.xmut.forma.pi.agent.event.PiEventType.AGENT_END) {
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
                .decision(com.xmut.forma.pi.agent.tool.ToolDecision.APPROVE)
                .build());

        assertThat(ends.get()).isEqualTo(3);
    }

    @Test
    void command_throw_is_swallowed_and_prompt_continues() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        PiExtension boom = bus -> bus.register(com.xmut.forma.pi.agent.event.PiEventType.COMMAND, e -> {
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
        PiExtension boom = bus -> bus.register(com.xmut.forma.pi.agent.event.PiEventType.BEFORE_AGENT_START, (mod, e) -> {
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
    void before_agent_start_leaves_turn_messages_unprefixed_and_skips_before_model_request() {
        String prefix = "R\n";
        AtomicReference<BeforeAgentStartEvent> seen = new AtomicReference<BeforeAgentStartEvent>();
        PiExtension ext = bus -> bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> {
            seen.set((BeforeAgentStartEvent) e.getPayload());
            mod.setUser(prefixLastUser(prefix));
        });
        when(conversationLoop.run(any(TurnInput.class), any())).thenAnswer(invocation -> {
            TurnInput in = invocation.getArgument(0);
            return ConversationResult.ok("r1", "ok", in.getMessages());
        });

        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-rem").source("api").build());
        sessionStore.append("s-rem", "seed",
                Collections.singletonList(Message.assistant("kept", Collections.emptyList())));
        DefaultAgentSession session = sessionWith(runner(ext), testLoader());
        List<PiEvent> events = new ArrayList<PiEvent>();
        session.subscribe(events::add);

        session.prompt(PromptRequest.builder()
                .sessionId("s-rem")
                .text("原文")
                .context("PAGE")
                .skillId("ecommerce-skulist")
                .workspaceRoot("/tmp/ws")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        List<Message> sent = cap.getValue().getMessages();
        assertThat(lastUser(sent).getContent()).isEqualTo("原文");
        assertThat(sent.get(0).getContent()).isEqualTo("kept");
        assertThat(seen.get().getSkillId()).isEqualTo("ecommerce-skulist");
        assertThat(seen.get().getWorkspaceRoot()).isEqualTo("/tmp/ws");
        assertThat(seen.get().getUserText()).isEqualTo("原文");
        assertThat(seen.get().getPageContext()).isEqualTo("PAGE");

        List<Message> stored = sessionStore.load("s-rem");
        assertThat(stored.get(0).getContent()).isEqualTo("kept");
        assertThat(stored).extracting(Message::getContent).contains("原文");
        assertThat(stored).extracting(Message::getContent).doesNotContain(prefix + "原文");

        session.prompt(PromptRequest.builder()
                .sessionId("s-rem")
                .text("下一句")
                .skillId("ecommerce-skulist")
                .build());

        verify(conversationLoop, times(2)).run(cap.capture(), any());
        List<Message> second = cap.getValue().getMessages();
        assertThat(firstUser(second).getContent()).isEqualTo("原文");
        assertThat(lastUser(second).getContent()).isEqualTo("下一句");
    }

    @Test
    void resume_rewrites_human_input_via_user_prompt_and_leaves_stored_history() {
        String prefix = "R\n";
        AtomicReference<BeforeAgentStartEvent> seen = new AtomicReference<BeforeAgentStartEvent>();
        PiExtension ext = bus -> bus.register(PiEventType.BEFORE_AGENT_START, (mod, e) -> {
            seen.set((BeforeAgentStartEvent) e.getPayload());
            mod.setUser(prefixLastUser(prefix));
        });
        List<Message> history = Collections.singletonList(Message.user("historical"));
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-res").source("api").build());
        sessionStore.append("s-res", "seed", history);
        when(conversationLoop.resume(any(ResumeRequest.class), any()))
                .thenReturn(ConversationResult.ok("r-res", "ok", history));
        DefaultAgentSession session = sessionWith(runner(ext), testLoader());
        List<PiEvent> events = new ArrayList<PiEvent>();
        session.subscribe(events::add);

        session.resume(ResumeRequest.builder()
                .runId("r-res")
                .sessionId("s-res")
                .workspaceRoot("/tmp/ws")
                .humanInput("confirm_execute go")
                .decision(com.xmut.forma.pi.agent.tool.ToolDecision.APPROVE)
                .build());

        ArgumentCaptor<ResumeRequest> cap = ArgumentCaptor.forClass(ResumeRequest.class);
        verify(conversationLoop).resume(cap.capture(), any());
        assertThat(cap.getValue().getHumanInput()).isEqualTo(prefix + "confirm_execute go");
        assertThat(seen.get().getSkillId()).isNull();
        assertThat(seen.get().getWorkspaceRoot()).isEqualTo("/tmp/ws");
        assertThat(seen.get().getUserText()).isEqualTo("confirm_execute go");
        assertThat(seen.get().getPageContext()).isNull();
        assertThat(sessionStore.load("s-res")).extracting(Message::getContent).containsExactly("historical");
        assertThat(events).extracting(PiEvent::getType).contains(PiEventType.BEFORE_AGENT_START);
    }

    private static UserModifier prefixLastUser(final String prefix) {
        return new UserModifier() {
            @Override
            public List<Message> apply(List<Message> messages) {
                if (messages == null || messages.isEmpty()) {
                    return messages;
                }
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message message = messages.get(i);
                    if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                        List<Message> out = new ArrayList<Message>(messages);
                        out.set(i, Message.user(prefix + message.getContent()));
                        return out;
                    }
                }
                return messages;
            }
        };
    }

    private static Message firstUser(List<Message> messages) {
        for (Message message : messages) {
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private static Message lastUser(List<Message> messages) {
        Message found = null;
        for (Message message : messages) {
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                found = message;
            }
        }
        return found;
    }

    @Test
    void explicit_skill_id_not_overwritten_by_template() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.ok("r1", "ok", Collections.emptyList()));
        DefaultAgentSession session = sessionWith(runner(), testLoader());

        session.prompt(PromptRequest.builder()
                .text("/echo x")
                .skillId("ecommerce-picklist")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getSkillId()).isEqualTo("ecommerce-picklist");
        assertThat(cap.getValue().getMessages().get(0).getContent()).isEqualTo("echo: x");
    }

    private DefaultAgentSession sessionWith(ExtensionRunner runner, DefaultPiResourceLoader loader) {
        com.xmut.forma.pi.agent.event.PiEventBus bus = new com.xmut.forma.pi.agent.event.DefaultPiEventBus();
        if (runner != null) {
            runner.register(bus);
        }
        return new DefaultAgentSession(conversationLoop, sessionStore, loader, bus);
    }

    private static ExtensionRunner runner(PiExtension... extras) {
        java.util.List<PiExtension> exts = new java.util.ArrayList<>();
        exts.add(new ToolPolicyExtension(InMemoryToolCatalog.empty()));
        if (extras != null) {
            Collections.addAll(exts, extras);
        }
        return new ExtensionRunner(exts);
    }

    private static DefaultPiResourceLoader testLoader() {
        return new DefaultPiResourceLoader();
    }
}

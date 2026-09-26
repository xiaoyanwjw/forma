package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.agent.Agent;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAgentSessionTest {

    @Mock
    private Agent conversationLoop;

    private InMemorySessionStore sessionStore;
    private DefaultAgentSession session;

    @BeforeEach
    void setUp() {
        sessionStore = new InMemorySessionStore();
        session = new DefaultAgentSession(conversationLoop, sessionStore);
    }

    @Test
    void prompt_null_conversationRequest_failed() {
        TurnResult result = session.prompt(null);
        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.FAILED);
        assertThat(result.getFinalResponse()).contains("PromptRequest required");
    }

    @Test
    void prompt_blank_tenant_still_creates_session() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    return ConversationResult.ok(req.getRunId(), "ok", req.getMessages());
                });
        TurnResult result = session.prompt(PromptRequest.builder().text("hi").build());
        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getSessionId()).isNotBlank();
    }

    @Test
    void prompt_maps_multimodal_messages_skill_and_ids() {
        Message user = Message.user(Arrays.asList(
                ContentPart.text("ocr"),
                ContentPart.imageUrl("https://img/cert.png")));
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    List<Message> out = new ArrayList<>(req.getMessages());
                    out.add(Message.assistant("ok", Collections.emptyList()));
                    return ConversationResult.ok(req.getRunId(), "ok", out);
                });

        TurnResult result = session.prompt(PromptRequest.builder()
                .traceId("tr-1")
                .skillId("ecommerce-picklist")
                .messages(Collections.singletonList(user))
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getSessionId()).isNotBlank();

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        TurnInput mapped = cap.getValue();
        assertThat(mapped.getTraceId()).isEqualTo("tr-1");
        assertThat(mapped.getSkillId()).isEqualTo("ecommerce-picklist");
        assertThat(mapped.getSessionId()).isEqualTo(result.getSessionId());
        assertThat(mapped.getMessages()).hasSize(1);
        assertThat(mapped.getMessages().get(0).hasImagePart()).isTrue();
        assertThat(sessionStore.load(result.getSessionId())).hasSize(2);
    }

    @Test
    void prompt_text_becomes_user_history_when_messages_empty() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    List<Message> out = new ArrayList<>(req.getMessages());
                    out.add(Message.assistant("hello", Collections.emptyList()));
                    return ConversationResult.ok(req.getRunId(), "hello", out);
                });

        session.prompt(PromptRequest.builder()
                .text("hello")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        // Session 路径：本轮 user 只在 TurnInput.messages
        assertThat(cap.getValue().getMessages()).hasSize(1);
        assertThat(cap.getValue().getMessages().get(0).getContent()).isEqualTo("hello");
    }

    @Test
    void prompt_failed_does_not_append_session_messages() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.failed("run-1", "boom"));

        TurnResult result = session.prompt(PromptRequest.builder()
                .sessionId("s-fail")
                .text("hi")
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.FAILED);
        assertThat(sessionStore.load("s-fail")).isEmpty();
    }

    @Test
    void prompt_second_turn_only_text_sees_hydrated_history() {
        AtomicReference<List<Message>> secondHistory = new AtomicReference<>();
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    List<Message> hist = req.getMessages();
                    if ("run-2".equals(req.getRunId())) {
                        secondHistory.set(new ArrayList<>(hist));
                    }
                    List<Message> out = new ArrayList<>(hist);
                    out.add(Message.assistant("a-" + req.getRunId(), Collections.emptyList()));
                    return ConversationResult.ok(req.getRunId(), "ok", out);
                });

        TurnResult t1 = session.prompt(PromptRequest.builder()
                .sessionId("s-multi")
                .runId("run-1")
                .text("first")
                .build());
        assertThat(t1.getStatus()).isEqualTo(TurnResult.Status.OK);

        TurnResult t2 = session.prompt(PromptRequest.builder()
                .sessionId("s-multi")
                .runId("run-2")
                .text("second")
                .build());
        assertThat(t2.getStatus()).isEqualTo(TurnResult.Status.OK);

        assertThat(secondHistory.get()).isNotNull();
        assertThat(secondHistory.get().size()).isGreaterThanOrEqualTo(3);
        assertThat(secondHistory.get().get(0).getContent()).isEqualTo("first");
        assertThat(secondHistory.get().get(1).getContent()).isEqualTo("a-run-1");
        assertThat(secondHistory.get().get(2).getContent()).isEqualTo("second");

        // 第三轮条数继续递增
        session.prompt(PromptRequest.builder()
                .sessionId("s-multi")
                .runId("run-3")
                .text("third")
                .build());
        assertThat(sessionStore.load("s-multi").size()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void prompt_omit_sessionId_starts_fresh_without_other_session_history() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-old").build());
        sessionStore.append("s-old", "old",
                Arrays.asList(Message.user("old-u"), Message.assistant("old-a", Collections.emptyList())));

        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    List<Message> out = new ArrayList<>(req.getMessages());
                    out.add(Message.assistant("fresh", Collections.emptyList()));
                    return ConversationResult.ok(req.getRunId(), "ok", out);
                });

        TurnResult result = session.prompt(PromptRequest.builder()
                .runId("run-new")
                .text("only-new")
                .build());

        ArgumentCaptor<TurnInput> cap = ArgumentCaptor.forClass(TurnInput.class);
        verify(conversationLoop).run(cap.capture(), any());
        assertThat(cap.getValue().getMessages())
                .extracting(Message::getContent)
                .containsExactly("only-new");
        assertThat(result.getSessionId()).isNotEqualTo("s-old");
        assertThat(sessionStore.load("s-old")).extracting(Message::getContent)
                .containsExactly("old-u", "old-a");
        assertThat(sessionStore.load(result.getSessionId()).stream().map(Message::getContent))
                .contains("only-new", "fresh");
    }

    @Test
    void mapStatus_null_is_failed() {
        assertThat(DefaultAgentSession.mapStatus(null)).isEqualTo(TurnResult.Status.FAILED);
    }

    @Test
    void resume_without_tenant_delegates_to_loop() {
        when(conversationLoop.resume(any(ResumeRequest.class), any()))
                .thenReturn(ConversationResult.ok("r1", "resumed", Collections.emptyList()));
        TurnResult result = session.resume(ResumeRequest.builder()
                .runId("r1")
                .decision(com.xmut.lims.pi.agent.tool.ToolDecision.APPROVE)
                .build());
        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("resumed");
        assertThat(result.getFinalResponse()).doesNotContain("tenantId required");
        verify(conversationLoop).resume(any(ResumeRequest.class), any());
    }

    @Test
    void computeAppendDelta_takes_suffix_after_hydrate_base() {
        List<Message> base = Arrays.asList(Message.user("u"), Message.assistant("a", null));
        List<Message> result = Arrays.asList(
                Message.user("u"),
                Message.assistant("a", null),
                Message.user("new-u"),
                Message.assistant("new", null),
                Message.system("skip"));
        assertThat(Session.computeAppendDelta(base, result))
                .extracting(Message::getContent)
                .containsExactly("new-u", "new");
    }

    @Test
    void computeAppendDelta_prefix_mismatch_returns_null() {
        List<Message> base = Arrays.asList(Message.user("u"), Message.assistant("a", null));
        List<Message> rewritten = Arrays.asList(
                Message.assistant("summary", null),
                Message.user("new-u"),
                Message.assistant("new", null));
        assertThat(Session.computeAppendDelta(base, rewritten)).isNull();
    }

    @Test
    void prompt_prefix_mismatch_forks_new_session_and_leaves_old_intact() {
        sessionStore.getOrCreate(Session.Meta.builder().sessionId("s-fork").build());
        sessionStore.append("s-fork", "seed",
                Arrays.asList(Message.user("old-u"), Message.assistant("old-a", Collections.emptyList())));

        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenAnswer(inv -> {
                    TurnInput req = inv.getArgument(0);
                    // 模拟压缩改写：图态不再以 hydrate 前缀开头
                    List<Message> rewritten = new ArrayList<>();
                    rewritten.add(Message.assistant("compressed-summary", Collections.emptyList()));
                    rewritten.add(Message.user("new-u"));
                    rewritten.add(Message.assistant("new-a", Collections.emptyList()));
                    return ConversationResult.ok(req.getRunId(), "ok", rewritten);
                });

        TurnResult result = session.prompt(PromptRequest.builder()
                .sessionId("s-fork")
                .runId("run-fork")
                .text("new-u")
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getSessionId()).isNotEqualTo("s-fork");
        assertThat(sessionStore.load("s-fork")).extracting(Message::getContent)
                .containsExactly("old-u", "old-a");
        assertThat(sessionStore.load(result.getSessionId())).extracting(Message::getContent)
                .containsExactly("compressed-summary", "new-u", "new-a");
        Optional<Session> child = sessionStore.find(result.getSessionId());
        assertThat(child).isPresent();
        assertThat(child.get().getParentSessionId()).isEqualTo("s-fork");
    }
}

package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.agent.ConversationResult;
import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.agent.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAgentSessionSubscribeTest {

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
    void subscribe_receives_agent_start_then_agent_end() {
        when(conversationLoop.run(any(TurnInput.class), any())).thenAnswer(inv -> {
            TurnInput t = inv.getArgument(0);
            return ConversationResult.ok(t.getRunId(), "ok",
                    new ArrayList<>(t.getMessages()));
        });

        List<PiEvent> events = new ArrayList<>();
        AutoCloseable sub = session.subscribe(events::add);

        TurnResult result = session.prompt(PromptRequest.builder().text("hi").build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getSessionId()).isNotBlank();
        assertThat(result.getFinalResponse()).isEqualTo("ok");
        assertThat(events).extracting(PiEvent::getType)
                .containsExactly(
                        PiEventType.COMMAND,
                        PiEventType.BEFORE_AGENT_START,
                        PiEventType.AGENT_START,
                        PiEventType.AGENT_END);
        assertThat(events.get(2).getPayload()).isEqualTo(result.getSessionId());
        assertThat(events.get(3).getPayload()).isSameAs(result);
        verify(conversationLoop).run(any(TurnInput.class), any());
        closeQuietly(sub);
    }

    @Test
    void subscribe_close_stops_delivery() {
        when(conversationLoop.run(any(TurnInput.class), any())).thenAnswer(inv -> {
            TurnInput t = inv.getArgument(0);
            return ConversationResult.ok(t.getRunId(), "ok",
                    new ArrayList<>(t.getMessages()));
        });

        AtomicInteger hits = new AtomicInteger();
        AutoCloseable sub = session.subscribe(e -> hits.incrementAndGet());
        closeQuietly(sub);

        session.prompt(PromptRequest.builder().text("hi").build());
        assertThat(hits.get()).isZero();
    }

    @Test
    void prompt_suspended_emits_suspended_before_agent_end() {
        when(conversationLoop.run(any(TurnInput.class), any()))
                .thenReturn(ConversationResult.suspended("r-hitl", "suspended at node: tools"));

        List<PiEvent> events = new ArrayList<>();
        session.subscribe(events::add);

        TurnResult result = session.prompt(PromptRequest.builder()
                .runId("r-hitl")
                .text("save")
                .build());

        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.SUSPENDED);
        assertThat(result.getRunId()).isEqualTo("r-hitl");
        assertThat(events).extracting(PiEvent::getType)
                .containsExactly(
                        PiEventType.COMMAND,
                        PiEventType.BEFORE_AGENT_START,
                        PiEventType.AGENT_START,
                        PiEventType.SUSPENDED,
                        PiEventType.AGENT_END);
        assertThat(events.get(3).getPayload()).isSameAs(result);
        assertThat(events.get(4).getPayload()).isSameAs(result);
    }

    @Test
    void subscribe_null_handler_throws_npe() {
        assertThatThrownBy(() -> session.subscribe(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    void prompt_without_tenant_completes_ok() {
        when(conversationLoop.run(any(TurnInput.class), any())).thenAnswer(inv -> {
            TurnInput t = inv.getArgument(0);
            return ConversationResult.ok(t.getRunId(), "ok",
                    new ArrayList<>(t.getMessages()));
        });
        TurnResult result = session.prompt(PromptRequest.builder().text("hi").build());
        assertThat(result.getStatus()).isEqualTo(TurnResult.Status.OK);
        assertThat(result.getFinalResponse()).doesNotContain("tenantId required");
        assertThat(result.getSessionId()).isNotBlank();
    }

    private static void closeQuietly(AutoCloseable sub) {
        try {
            sub.close();
        } catch (Exception ignored) {
            // test cleanup
        }
    }
}

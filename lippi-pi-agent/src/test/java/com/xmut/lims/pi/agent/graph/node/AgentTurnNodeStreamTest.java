package com.xmut.lims.pi.agent.graph.node;

import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.event.PiEvent;
import com.xmut.lims.pi.agent.event.PiEventType;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnNodeStreamTest {

    @Test
    void complete_only_provider_emits_one_text_delta() {
        ModelProvider fake = request -> ModelResponse.builder()
                .content("hello-delta")
                .toolCalls(Collections.emptyList())
                .finishReason("stop")
                .build();

        List<PiEvent> events = new ArrayList<>();
        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> updates = node.execute(
                GraphState.create(userTurn()),
                new NodeContext("r1", "tr1", recording(events)));

        assertThat(events).extracting(PiEvent::getType)
                .containsExactly(PiEventType.MESSAGE_UPDATE);
        assertThat(events.get(0).getPayload()).isEqualTo("hello-delta");
        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("hello-delta");
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs.get(1).getRole()).isEqualTo("assistant");
        assertThat(msgs.get(1).getContent()).isEqualTo("hello-delta");
    }

    @Test
    void emitter_throw_still_writes_state() {
        ModelProvider fake = request -> ModelResponse.builder()
                .content("answer")
                .toolCalls(Collections.emptyList())
                .finishReason("stop")
                .build();

        AtomicInteger hits = new AtomicInteger();
        Emitter emitter = new Emitter() {
            @Override
            public void emit(PiEvent event) {
                hits.incrementAndGet();
                throw new RuntimeException("observer boom");
            }

            @Override
            public <T> T emit(PiEvent event, Class<T> resultType) {
                emit(event);
                return null;
            }
        };

        Map<String, Object> updates = new AgentTurnNode(fake).execute(
                GraphState.create(userTurn()),
                new NodeContext("r1", "tr1", emitter));

        assertThat(hits.get()).isEqualTo(1);
        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("answer");
    }

    private static Map<String, Object> userTurn() {
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("q").build()));
        return initial;
    }

    private static Emitter recording(List<PiEvent> events) {
        return new Emitter() {
            @Override
            public void emit(PiEvent event) {
                events.add(event);
            }

            @Override
            public <T> T emit(PiEvent event, Class<T> resultType) {
                emit(event);
                return null;
            }
        };
    }
}

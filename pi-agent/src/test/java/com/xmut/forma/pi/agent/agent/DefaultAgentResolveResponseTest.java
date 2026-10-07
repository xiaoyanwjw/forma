package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.graph.GraphState;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DefaultAgent#resolveResponse} 只投影 transcript / LLM 原文，不解释业务 view JSON。
 */
class DefaultAgentResolveResponseTest {

    @Test
    void prefersLastNonBlankAssistantOverEarlierViewShapedJson() {
        GraphState state = state(
                Message.user("hi"),
                Message.assistant("{\"view\":{\"version\":1},\"plan\":\"old\"}", Collections.emptyList()),
                Message.assistant("final narration", Collections.emptyList()));
        state = state.withUpdate(Collections.<String, Object>singletonMap(StateKeys.LLM_RESPONSE, "stale-llm"));

        assertThat(DefaultAgent.resolveResponse(state)).isEqualTo("final narration");
    }

    @Test
    void fallsBackToLlmResponseWhenAssistantContentBlank() {
        GraphState state = state(Message.assistant("   ", Collections.emptyList()));
        state = state.withUpdate(Collections.<String, Object>singletonMap(StateKeys.LLM_RESPONSE, "from-llm"));

        assertThat(DefaultAgent.resolveResponse(state)).isEqualTo("from-llm");
    }

    @Test
    void doesNotPreferViewKeyInsideAssistantBody() {
        GraphState state = state(
                Message.assistant("{\"view\":{\"version\":1}}", Collections.emptyList()),
                Message.assistant("ok without view key", Collections.emptyList()));

        assertThat(DefaultAgent.resolveResponse(state)).isEqualTo("ok without view key");
    }

    private static GraphState state(Message... messages) {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put(StateKeys.MESSAGES, Arrays.asList(messages));
        return GraphState.create(values);
    }
}

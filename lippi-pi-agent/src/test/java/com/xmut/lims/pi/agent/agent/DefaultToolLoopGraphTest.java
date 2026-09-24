package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.graph.CompileConfig;
import com.xmut.lims.pi.agent.graph.CompiledGraph;
import com.xmut.lims.pi.agent.graph.GraphCompilationException;
import com.xmut.lims.pi.agent.graph.GraphOutcome;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.RunnableConfig;
import com.xmut.lims.pi.agent.graph.StateGraph;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultToolLoopGraphTest {

    @Test
    void defaultTopology_noToolCalls_endsWithOkPath() {
        StateGraph graph = DefaultToolLoopGraph.build();
        CompiledGraph compiled = graph.compile(CompileConfig.builder().maxSupersteps(10).build());

        Map<String, Object> input = new HashMap<>();
        input.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("ping").build()));

        GraphOutcome outcome = compiled.invoke(input, RunnableConfig.of("r1", "tr1"));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.getFinalState().get(StateKeys.LLM_RESPONSE, String.class)).isEqualTo("ping");
        @SuppressWarnings("unchecked")
        List<?> calls = outcome.getFinalState().get(StateKeys.TOOL_CALLS, List.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void factory_exposesFixedNodeNames() {
        assertThat(DefaultToolLoopGraph.NODE_AGENT).isEqualTo("agent");
        assertThat(DefaultToolLoopGraph.NODE_TOOLS).isEqualTo("tools");
    }

    @Test
    void factory_hasNoLoadMemoryOrPolicyNodes() {
        StateGraph graph = DefaultToolLoopGraph.build();
        assertThatThrownBy(() -> graph.compile(CompileConfig.builder()
                .interruptBefore(Collections.singletonList("load_memory")).build()))
                .isInstanceOf(GraphCompilationException.class)
                .hasMessageContaining("load_memory");
        assertThatThrownBy(() -> DefaultToolLoopGraph.build().compile(CompileConfig.builder()
                .interruptBefore(Collections.singletonList("policy")).build()))
                .isInstanceOf(GraphCompilationException.class)
                .hasMessageContaining("policy");
        assertThatThrownBy(() -> DefaultToolLoopGraph.build().compile(CompileConfig.builder()
                .interruptBefore(Collections.singletonList("human")).build()))
                .isInstanceOf(GraphCompilationException.class)
                .hasMessageContaining("human");
        assertThatThrownBy(() -> DefaultToolLoopGraph.build().compile(CompileConfig.builder()
                .interruptBefore(Collections.singletonList("tool_call")).build()))
                .isInstanceOf(GraphCompilationException.class)
                .hasMessageContaining("tool_call");
    }

    @Test
    void hasToolCalls_routesToToolsOrEnd() {
        Map<String, Object> withCalls = new HashMap<>();
        withCalls.put(StateKeys.TOOL_CALLS, Collections.singletonList(
                new ToolCallEntry("c1", "t", null)));
        assertThat(DefaultToolLoopGraph.hasToolCalls().route(GraphState.create(withCalls)))
                .isEqualTo("tools");
        assertThat(DefaultToolLoopGraph.hasToolCalls().route(GraphState.empty()))
                .isEqualTo("end");
    }

    @Test
    void injectableAgentNode_used() {
        StateGraph graph = DefaultToolLoopGraph.create(
                (state, ctx) -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put(StateKeys.TOOL_CALLS, Collections.emptyList());
                    updates.put(StateKeys.LLM_RESPONSE, "custom");
                    return updates;
                },
                DefaultToolConfig.empty());

        GraphOutcome outcome = graph.compile().invoke(Collections.emptyMap(), RunnableConfig.of("r1", "tr1"));
        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.getFinalState().get(StateKeys.LLM_RESPONSE, String.class)).isEqualTo("custom");
    }
}

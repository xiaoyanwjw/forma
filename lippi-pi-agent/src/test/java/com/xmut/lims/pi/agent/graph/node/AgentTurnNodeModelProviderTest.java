package com.xmut.lims.pi.agent.graph.node;


import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.agent.PromptBuilder;
import com.xmut.lims.pi.agent.agent.SystemPromptInput;
import com.xmut.lims.pi.agent.agent.SystemPromptStable;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTurnNodeModelProviderTest {

    @Test
    void model_request_fills_session_id_from_state() {
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request);
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("q").build()));
        initial.put(StateKeys.SESSION_ID, "sess-1");

        node.execute(GraphState.create(initial), new NodeContext("r1", "tr1"));

        assertThat(captured.get().getSessionId()).isEqualTo("sess-1");
        assertThat(captured.get().getRunId()).isEqualTo("r1");
    }

    @Test
    void fake_provider_direct_end_writes_llm_response_and_empty_tool_calls() {
        ModelProvider fake = request -> ModelResponse.builder()
                .content("answer")
                .toolCalls(Collections.emptyList())
                .finishReason("stop")
                .build();

        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("q").build()));
        GraphState state = GraphState.create(initial);

        Map<String, Object> updates = node.execute(state, new NodeContext("r1", "tr1"));

        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("answer");
        assertThat((List<?>) updates.get(StateKeys.TOOL_CALLS)).isEmpty();
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(1).getRole()).isEqualTo("assistant");
        assertThat(msgs.get(1).getContent()).isEqualTo("answer");
    }

    @Test
    void fake_provider_with_tool_calls_one_round() {
        AtomicInteger visits = new AtomicInteger();
        ModelProvider fake = request -> {
            int v = visits.incrementAndGet();
            if (v == 1) {
                return ModelResponse.builder()
                        .content(null)
                        .toolCalls(Collections.singletonList(
                                new ToolCallEntry("c1", "echo",
                                        JsonNodeFactory.instance.objectNode())))
                        .finishReason("tool_calls")
                        .build();
            }
            return ModelResponse.builder()
                    .content("done")
                    .toolCalls(Collections.emptyList())
                    .finishReason("stop")
                    .build();
        };

        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("q").build()));
        GraphState state = GraphState.create(initial);

        Map<String, Object> first = node.execute(state, new NodeContext("r1", "tr"));
        assertThat((List<?>) first.get(StateKeys.TOOL_CALLS)).hasSize(1);

        // 对齐开源 pi：tool 结果已由 ToolNode 写入 MESSAGES
        List<Message> afterToolMsgs = Message.withToolResults(
                first.get(StateKeys.MESSAGES),
                Collections.singletonList(ToolResult.ok("c1", "echo", "echoed")));
        Map<String, Object> afterTools = new HashMap<>();
        afterTools.put(StateKeys.MESSAGES, afterToolMsgs);
        afterTools.put(StateKeys.TOOL_CALLS, Collections.emptyList());
        GraphState state2 = GraphState.create(afterTools);

        Map<String, Object> second = node.execute(state2, new NodeContext("r1", "tr"));
        assertThat(second.get(StateKeys.LLM_RESPONSE)).isEqualTo("done");
        assertThat((List<?>) second.get(StateKeys.TOOL_CALLS)).isEmpty();
        assertThat(visits.get()).isEqualTo(2);
    }

    @Test
    void feeds_tool_results_already_in_messages_to_model() {
        ModelProvider fake = request -> {
            assertThat(request.getMessages()).anyMatch(m ->
                    "tool".equals(m.getRole()) && "tool-out".equals(m.getContent()));
            return ModelResponse.builder()
                    .content("after")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Message.withToolResults(
                Collections.singletonList(Message.builder().role("user").content("q").build()),
                Collections.singletonList(ToolResult.ok("c1", "echo", "tool-out"))));
        GraphState state = GraphState.create(initial);

        Map<String, Object> updates = node.execute(state, new NodeContext("r", "tr"));
        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("after");
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs).anyMatch(m ->
                "tool".equals(m.getRole())
                        && "tool-out".equals(m.getContent())
                        && "c1".equals(m.getToolCallId()));
    }

    @Test
    void passes_available_tools_and_default_use_case() {
        ModelProvider fake = request -> {
            assertThat(request.getUseCase()).isEqualTo(
                    com.xmut.lims.pi.ai.model.InMemoryModelCatalog.DEFAULT_USE_CASE);
            assertThat(request.getTools()).hasSize(1);
            assertThat(request.getTools().get(0).getName()).isEqualTo("echo");
            return ModelResponse.builder()
                    .content(null)
                    .toolCalls(Collections.singletonList(
                            new ToolCallEntry("c1", "echo",
                                    JsonNodeFactory.instance.objectNode())))
                    .build();
        };
        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("q").build()));
        initial.put(StateKeys.AVAILABLE_TOOLS, Collections.singletonList(
                com.xmut.lims.pi.ai.model.ToolSchema.builder().name("echo").build()));
        GraphState state = GraphState.create(initial);

        Map<String, Object> updates = node.execute(state, new NodeContext("r", "tr"));
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs.get(1).getToolCalls()).hasSize(1);
        assertThat(msgs.get(1).getToolCalls().get(0).getToolName()).isEqualTo("echo");
    }

    @Test
    void model_request_uses_preformatted_system_prompt() {
        AtomicInteger formatCalls = new AtomicInteger();
        AtomicInteger systemBuilds = new AtomicInteger();
        PromptBuilder recording = new PromptBuilder() {

            @Override
            public List<Message> format(Message system, List<Message> messages) {
                formatCalls.incrementAndGet();
                List<Message> out = new ArrayList<>();
                if (system != null) {
                    out.add(system);
                }
                if (messages != null) {
                    out.addAll(messages);
                }
                return out;
            }

            @Override
            public SystemPromptStable stable(SystemPromptInput input) {
                return SystemPromptStable.builder()
                        .stable("S").context("").variable("").build();
            }

            @Override
            public Message system(SystemPromptInput input) {
                systemBuilds.incrementAndGet();
                return Message.system("SHOULD-NOT-BUILD");
            }
        };
        AtomicReference<List<Message>> seenByProvider = new AtomicReference<>();
        ModelProvider fake = request -> {
            seenByProvider.set(request.getMessages());
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };

        AgentTurnNode node = new AgentTurnNode(fake, recording);
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.SYSTEM_PROMPT, "PREFORMATTED");
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("turn-user").build()));
        GraphState state = GraphState.create(initial);

        node.execute(state, new NodeContext("r", "tr"));

        assertThat(systemBuilds.get()).isZero();
        assertThat(formatCalls.get()).isEqualTo(1);
        assertThat(seenByProvider.get().get(0).getRole()).isEqualTo("system");
        assertThat(seenByProvider.get().get(0).getContent()).isEqualTo("PREFORMATTED");
        assertThat(seenByProvider.get().get(1).getContent()).isEqualTo("turn-user");
    }

    @Test
    void messages_already_contain_user_transcript() {
        ModelProvider fake = request -> {
            assertThat(request.getMessages()).anyMatch(m ->
                    "user".equals(m.getRole()) && "turn-user".equals(m.getContent()));
            Message cached = request.getMessages().get(0);
            assertThat(cached.getRole()).isEqualTo("system");
            assertThat(cached.getContent()).contains("my-soul");
            return ModelResponse.builder()
                    .content("reply")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        AgentTurnNode node = new AgentTurnNode(fake);
        Map<String, Object> initial = new HashMap<>();
        List<Message> history = new ArrayList<>();
        history.add(Message.builder().role("user").content("hist").build());
        history.add(Message.builder().role("user").content("turn-user").build());
        initial.put(StateKeys.MESSAGES, history);
        initial.put(StateKeys.SYSTEM_PROMPT, "my-soul");
        GraphState state = GraphState.create(initial);

        Map<String, Object> updates = node.execute(state, new NodeContext("r", "tr"));
        @SuppressWarnings("unchecked")
        List<Message> msgs = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(msgs).extracting(Message::getContent)
                .containsExactly("hist", "turn-user", "reply");
        assertThat(msgs).noneMatch(m -> "system".equals(m.getRole()));
    }

    @Test
    void system_prompt_from_state_not_rebuilt() {
        AtomicInteger systemBuilds = new AtomicInteger();
        PromptBuilder counting = new PromptBuilder() {

            @Override
            public List<Message> format(Message system, List<Message> messages) {
                List<Message> out = new ArrayList<>();
                if (system != null) {
                    out.add(system);
                }
                if (messages != null) {
                    out.addAll(messages);
                }
                return out;
            }

            @Override
            public SystemPromptStable stable(SystemPromptInput input) {
                return SystemPromptStable.builder().stable("X").context("").variable("").build();
            }

            @Override
            public Message system(SystemPromptInput input) {
                systemBuilds.incrementAndGet();
                return Message.system("BUILT");
            }
        };
        ModelProvider fake = request -> ModelResponse.builder()
                .content("ok")
                .toolCalls(Collections.emptyList())
                .build();
        AgentTurnNode node = new AgentTurnNode(fake, counting);

        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.SYSTEM_PROMPT, "PREFORMATTED");
        initial.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("u1").build()));
        node.execute(GraphState.create(initial), new NodeContext("r", "tr"));

        Map<String, Object> second = new HashMap<>();
        second.put(StateKeys.MESSAGES, Collections.singletonList(
                Message.builder().role("user").content("u1").build()));
        second.put(StateKeys.SYSTEM_PROMPT, "PREFORMATTED");
        node.execute(GraphState.create(second), new NodeContext("r", "tr"));

        assertThat(systemBuilds.get()).isZero();
    }

    @Test
    void blank_call_id_tool_result_not_in_model_or_transcript() {
        ModelProvider fake = request -> {
            assertThat(request.getMessages()).noneMatch(m -> "tool".equals(m.getRole()));
            return ModelResponse.builder()
                    .content("ok")
                    .toolCalls(Collections.emptyList())
                    .build();
        };
        AgentTurnNode node = new AgentTurnNode(fake);
        // blank callId 的 tool 结果不应被 withToolResults 写进 transcript
        List<Message> msgs = Message.withToolResults(
                Collections.singletonList(Message.builder().role("user").content("q").build()),
                Collections.singletonList(ToolResult.ok(null, "echo", "orphan")));
        Map<String, Object> initial = new HashMap<>();
        initial.put(StateKeys.MESSAGES, msgs);
        GraphState state = GraphState.create(initial);

        Map<String, Object> updates = node.execute(state, new NodeContext("r", "tr"));
        @SuppressWarnings("unchecked")
        List<Message> out = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(out).noneMatch(m -> "tool".equals(m.getRole()));
    }
}

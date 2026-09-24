package com.xmut.lims.pi.agent.graph.node;

import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.agent.CompressionConfig;
import com.xmut.lims.pi.agent.agent.ContextCompressor;
import com.xmut.lims.pi.agent.agent.DefaultContextCompressor;
import com.xmut.lims.pi.agent.agent.DefaultPromptBuilder;
import com.xmut.lims.pi.agent.agent.SystemPromptInput;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentTurnNode × ContextCompressor 集成（Story 51-8）。
 */
class AgentTurnNodeCompressorTest {

    @Test
    void over_threshold_history_preformatted_system_unchanged() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(80)
                .protectLastK(2)
                .contextMaxChars(4_000)
                .build();
        ContextCompressor compressor = new DefaultContextCompressor(config);

        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request);
            return ModelResponse.builder().content("ok").toolCalls(Collections.emptyList()).build();
        };

        AgentTurnNode node = new AgentTurnNode(fake, new DefaultPromptBuilder(), compressor);

        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            history.add(Message.user("hist-" + i + "-" + repeat("q", 40)));
        }

        String systemText = new DefaultPromptBuilder().system(SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SOUL, "SOUL_KEEP",
                        SystemPromptInput.CORE, "CORE_KEEP"))
                .variable(SystemPromptInput.mapOf(SystemPromptInput.MEMORY, "RECALL_KEEP"))
                .build()).getContent();

        history.add(Message.user("latest"));
        Map<String, Object> init = new HashMap<>();
        init.put(StateKeys.MESSAGES, history);
        init.put(StateKeys.SYSTEM_PROMPT, systemText);

        Map<String, Object> updates = node.execute(
                GraphState.create(init),
                new NodeContext("r1", "tr1"));

        ModelRequest req = captured.get();
        assertThat(req).isNotNull();
        Message system = req.getMessages().get(0);
        assertThat(system.getRole()).isEqualToIgnoringCase("system");
        assertThat(system.getContent()).contains("SOUL_KEEP");
        assertThat(system.getContent()).contains("CORE_KEEP");
        assertThat(system.getContent()).contains("RECALL_KEEP");

        @SuppressWarnings("unchecked")
        List<Message> persisted = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertThat(persisted).isNotEmpty();
        long summaryCount = persisted.stream()
                .filter(m -> m.getContent() != null && m.getContent().startsWith("[context_summary]"))
                .count();
        assertThat(summaryCount).isEqualTo(1);
        for (Message m : persisted) {
            if (!"system".equalsIgnoreCase(m.getRole())) {
                assertThat(m.getContent()).doesNotContain("CORE_KEEP");
                assertThat(m.getContent()).doesNotContain("RECALL_KEEP");
            }
        }
        assertThat(persisted.size()).isLessThan(history.size() + 2);
    }

    @Test
    void compressor_does_not_rewrite_preformatted_system() {
        CompressionConfig config = CompressionConfig.builder()
                .maxPromptChars(100_000)
                .contextMaxChars(50)
                .build();
        ContextCompressor compressor = new DefaultContextCompressor(config);

        AtomicReference<String> seen = new AtomicReference<>();
        ModelProvider fake = request -> {
            seen.set(request.getMessages().get(0).getContent());
            return ModelResponse.builder().content("ok").toolCalls(Collections.emptyList()).build();
        };

        AgentTurnNode node = new AgentTurnNode(fake, new DefaultPromptBuilder(), compressor);

        String systemText = "SOUL_X\n\nCORE_X\n\n" + repeat("A", 200);
        Map<String, Object> init = new HashMap<>();
        init.put(StateKeys.MESSAGES, Collections.singletonList(Message.user("hi")));
        init.put(StateKeys.SYSTEM_PROMPT, systemText);

        node.execute(GraphState.create(init), new NodeContext("r1", "tr1"));

        assertThat(seen.get()).isEqualTo(systemText);
    }

    @Test
    void compressor_throws_turn_still_ok_model_still_called() {
        ContextCompressor boom = request -> {
            throw new IllegalStateException("compress boom");
        };
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request);
            return ModelResponse.builder().content("survived").toolCalls(Collections.emptyList()).build();
        };

        AgentTurnNode node = new AgentTurnNode(fake, new DefaultPromptBuilder(), boom);

        Map<String, Object> init = new HashMap<>();
        init.put(StateKeys.MESSAGES, Collections.singletonList(Message.user("q")));
        init.put(StateKeys.SYSTEM_PROMPT, "SOUL");

        Map<String, Object> updates = node.execute(
                GraphState.create(init),
                new NodeContext("r1", "tr1"));

        assertThat(captured.get()).isNotNull();
        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("survived");
        assertThat(captured.get().getMessages().get(0).getContent()).contains("SOUL");
    }

    @Test
    void noop_compressor_matches_prior_behavior() {
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelProvider fake = request -> {
            captured.set(request);
            return ModelResponse.builder().content("echo").toolCalls(Collections.emptyList()).build();
        };

        AgentTurnNode node = new AgentTurnNode(
                fake, new DefaultPromptBuilder(), ContextCompressor.NOOP);

        Map<String, Object> init = new HashMap<>();
        init.put(StateKeys.MESSAGES, Collections.singletonList(Message.user("hello")));
        init.put(StateKeys.SYSTEM_PROMPT, "POLICY");

        Map<String, Object> updates = node.execute(
                GraphState.create(init),
                new NodeContext("r1", "tr1"));

        assertThat(updates.get(StateKeys.LLM_RESPONSE)).isEqualTo("echo");
        assertThat(captured.get().getMessages()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(captured.get().getMessages().get(0).getContent()).contains("POLICY");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(n * s.length());
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}

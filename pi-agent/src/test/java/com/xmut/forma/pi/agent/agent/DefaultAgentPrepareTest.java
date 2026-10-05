package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.IterationBudget;
import com.xmut.forma.pi.agent.ResumeInput;
import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.node.AgentTurnNode;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.agent.tool.InMemoryToolCatalog;
import com.xmut.forma.pi.agent.tool.ToolDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentPrepareTest {

    @Test
    void prepare_derives_workspace_from_config_when_not_passed() {
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                new IterationBudget(25),
                InMemoryToolCatalog.empty(),
                null);
        Map<String, Object> input = loop.prepare(
                TurnInput.withUser("hi")
                        .sessionId("sess-a")
                        .runId("run-b")
                        .build(),
                InMemoryToolCatalog.empty(),
                null);
        assertThat((String) input.get(StateKeys.WORKSPACE_ROOT))
                .endsWith("/sessions/sess-a/run-b");
    }

    @Test
    void resume_prepare_writes_workspace_like_prompt() {
        DefaultAgent loop = agent();
        Map<String, Object> input = loop.prepare(
                ResumeInput.builder()
                        .runId("run-b")
                        .sessionId("sess-a")
                        .decision(ToolDecision.APPROVE)
                        .build());
        assertThat((String) input.get(StateKeys.WORKSPACE_ROOT))
                .endsWith("/sessions/sess-a/run-b");
        assertThat(input.get(StateKeys.TOOL_APPROVAL)).isEqualTo(ToolDecision.APPROVE);
    }

    @Test
    void resume_prepare_applies_user_modifier_to_human_input() {
        ContextModifier modifier = ContextModifier.empty();
        modifier.setUser(prefixLastUser("R\n"));
        Map<String, Object> input = agent().prepare(
                ResumeInput.builder()
                        .runId("run-b")
                        .decision(ToolDecision.APPROVE)
                        .humanInput("confirm")
                        .contextModifier(modifier)
                        .build());
        assertThat((String) input.get(StateKeys.HUMAN_INPUT)).isEqualTo("R\nconfirm");
    }

    @Test
    void prepare_applies_system_and_user_chains_then_drops_system_role() {
        ContextModifier modifier = ContextModifier.empty();
        modifier.getSystem().appendVariable("VOL-FROM-EXT");
        modifier.setUser(prefixLastUser("R\n"));
        DefaultAgent loop = agent();

        Map<String, Object> input = loop.prepare(
                TurnInput.builder()
                        .messages(Arrays.asList(Message.system("sys-hide"), Message.user("原文")))
                        .context("PAGE")
                        .contextModifier(modifier)
                        .build(),
                InMemoryToolCatalog.empty(),
                null);

        assertThat((String) input.get(StateKeys.SYSTEM_PROMPT)).contains("VOL-FROM-EXT");
        assertThat((String) input.get(StateKeys.SYSTEM_PROMPT)).contains("PAGE");
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) input.get(StateKeys.MESSAGES);
        assertThat(messages).extracting(Message::getContent).containsExactly("R\n原文");
        assertThat(messages).extracting(Message::getRole).doesNotContain("system");
    }

    private static DefaultAgent agent() {
        return new DefaultAgent(
                DefaultToolLoopGraph.create(AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                new IterationBudget(25),
                InMemoryToolCatalog.empty(),
                null);
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
}

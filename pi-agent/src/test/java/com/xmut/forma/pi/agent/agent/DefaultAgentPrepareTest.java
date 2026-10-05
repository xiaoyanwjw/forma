package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.IterationBudget;
import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.extension.UserModifier;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.node.AgentTurnNode;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.agent.tool.InMemoryToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentPrepareTest {

    @Test
    void prepare_writes_workspace_root_when_present() {
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(AgentTurnNode.forTopologyTest(), InMemoryToolCatalog.empty()),
                new InMemoryCheckpointer(),
                new InMemoryResumeIdempotencyStore(),
                new IterationBudget(25),
                InMemoryToolCatalog.empty(),
                null);
        Map<String, Object> input = loop.prepare(
                TurnInput.withUser("hi").workspaceRoot(" /tmp/ws/sessions/s/r ").build(),
                InMemoryToolCatalog.empty(),
                null);
        assertThat(input.get(StateKeys.WORKSPACE_ROOT)).isEqualTo("/tmp/ws/sessions/s/r");
    }

    @Test
    void applyWorkspaceRoot_request_overrides_stale_checkpoint_value() {
        Map<String, Object> input = new HashMap<>();
        input.put(StateKeys.WORKSPACE_ROOT, "/stale/from/checkpoint");
        DefaultAgent.applyWorkspaceRoot(
                ResumeRequest.builder().workspaceRoot(" /tmp/ws/sessions/s/r ").build(),
                input);
        assertThat(input.get(StateKeys.WORKSPACE_ROOT)).isEqualTo("/tmp/ws/sessions/s/r");
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

    @Test
    void applyWorkspaceRoot_blank_request_keeps_checkpoint_value() {
        Map<String, Object> input = new HashMap<>();
        input.put(StateKeys.WORKSPACE_ROOT, "/stale/from/checkpoint");
        DefaultAgent.applyWorkspaceRoot(ResumeRequest.builder().build(), input);
        assertThat(input.get(StateKeys.WORKSPACE_ROOT)).isEqualTo("/stale/from/checkpoint");
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

package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.agent.IterationBudget;
import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.TurnInput;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.forma.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.forma.pi.agent.graph.node.AgentTurnNode;
import com.xmut.forma.pi.agent.tool.InMemoryToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
    void applyWorkspaceRoot_blank_request_keeps_checkpoint_value() {
        Map<String, Object> input = new HashMap<>();
        input.put(StateKeys.WORKSPACE_ROOT, "/stale/from/checkpoint");
        DefaultAgent.applyWorkspaceRoot(ResumeRequest.builder().build(), input);
        assertThat(input.get(StateKeys.WORKSPACE_ROOT)).isEqualTo("/stale/from/checkpoint");
    }
}

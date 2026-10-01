package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.node.AgentTurnNode;
import com.xmut.lims.pi.agent.tool.InMemoryToolCatalog;
import org.junit.jupiter.api.Test;

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
}

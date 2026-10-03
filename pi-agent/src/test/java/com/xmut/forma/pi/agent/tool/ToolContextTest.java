package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.graph.GraphState;
import com.xmut.forma.pi.agent.graph.NodeContext;
import com.xmut.forma.pi.agent.graph.StateKeys;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolContextTest {

    @Test
    void from_readsWorkspaceRootFromState() {
        GraphState state = GraphState.create(Collections.singletonMap(
                StateKeys.WORKSPACE_ROOT, "/tmp/ws/sessions/s/r"));
        NodeContext node = new NodeContext("run-1", "tr-1");
        ToolContext ctx = ToolContext.from(node, state);
        assertEquals("/tmp/ws/sessions/s/r", ctx.getWorkspaceRoot());
    }
}

package com.xmut.lims.pi.agent.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryCheckpointer;
import com.xmut.lims.pi.agent.graph.node.AgentTurnNode;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentTurnNode + FakeProvider 经完整 ConversationLoop 一圈 / 直接 END。
 */
class AgentTurnNodeLoopIntegrationTest {

    @Test
    void fake_provider_direct_end_ok() {
        ModelProvider fake = request -> ModelResponse.builder()
                .content("direct")
                .toolCalls(Collections.emptyList())
                .build();
        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.build(fake),
                new InMemoryCheckpointer());

        ConversationResult result = loop.run(TurnInput.withUser("q")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("direct");
    }

    @Test
    void fake_provider_one_tool_round_then_end() {
        AtomicInteger visits = new AtomicInteger();
        ModelProvider fake = request -> {
            if (visits.incrementAndGet() == 1) {
                return ModelResponse.builder()
                        .content(null)
                        .toolCalls(Collections.singletonList(
                                new ToolCallEntry("c1", "echo",
                                        JsonNodeFactory.instance.objectNode())))
                        .finishReason("tool_calls")
                        .build();
            }
            return ModelResponse.builder()
                    .content("after-tool")
                    .toolCalls(Collections.emptyList())
                    .build();
        };

        Map<String, ToolHandler> handlers = new HashMap<>();
        handlers.put("echo", (call, ctx) -> ToolResult.ok(call.getId(), "echo", "ok"));
        DefaultToolConfig policy = ToolTestSupport.readConfig(handlers);

        DefaultAgent loop = new DefaultAgent(
                DefaultToolLoopGraph.create(new AgentTurnNode(fake), policy),
                new InMemoryCheckpointer(),
                policy);

        ConversationResult result = loop.run(TurnInput.withUser("q")
                .build());

        assertThat(result.getStatus()).isEqualTo(ConversationResult.Status.OK);
        assertThat(result.getFinalResponse()).isEqualTo("after-tool");
        assertThat(visits.get()).isEqualTo(2);
    }
}

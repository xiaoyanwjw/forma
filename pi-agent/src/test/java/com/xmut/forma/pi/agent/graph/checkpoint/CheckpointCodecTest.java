package com.xmut.forma.pi.agent.graph.checkpoint;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xmut.forma.pi.agent.graph.GraphState;
import com.xmut.forma.pi.agent.graph.StateKeys;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.agent.tool.ToolDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphState typed 往返：MESSAGES（含 ToolCallEntry）+ TOOL_CALLS + TOOL_APPROVAL。
 */
class CheckpointCodecTest {

    private final CheckpointCodec codec = new CheckpointCodec();

    @Test
    void roundTrip_preservesMessageToolCallsAndApproval() throws Exception {
        ToolCallEntry call = new ToolCallEntry(
                "c1", "save", JsonNodeFactory.instance.objectNode().put("x", 1));
        Message assistant = Message.assistant("need write", Collections.singletonList(call));

        Map<String, Object> values = new HashMap<>();
        values.put(StateKeys.MESSAGES, Collections.singletonList(assistant));
        values.put(StateKeys.TOOL_CALLS, Collections.singletonList(call));
        values.put(StateKeys.TOOL_APPROVAL, ToolDecision.APPROVE);
        values.put(StateKeys.LLM_RESPONSE, "pending");
        GraphState state = GraphState.create(values);

        Checkpoint original = new Checkpoint(
                "cp-1", "run-1", 3, "human", state, Instant.parse("2026-08-06T04:00:00Z"),
                Collections.singletonMap("k", "v"));

        String json = codec.encode(original);
        Checkpoint decoded = codec.decode(json).orElseThrow(AssertionError::new);

        assertThat(decoded.getCheckpointId()).isEqualTo("cp-1");
        assertThat(decoded.getRunId()).isEqualTo("run-1");
        assertThat(decoded.getStep()).isEqualTo(3);
        assertThat(decoded.getCurrentNode()).isEqualTo("human");

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) decoded.getState().get(StateKeys.MESSAGES);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(Message.class);
        assertThat(messages.get(0).getToolCalls()).hasSize(1);
        assertThat(messages.get(0).getToolCalls().get(0).getToolName()).isEqualTo("save");
        assertThat(messages.get(0).getToolCalls().get(0).getArguments().get("x").asInt()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<ToolCallEntry> calls = (List<ToolCallEntry>) decoded.getState().get(StateKeys.TOOL_CALLS);
        assertThat(calls.get(0)).isInstanceOf(ToolCallEntry.class);
        assertThat(calls.get(0).getId()).isEqualTo("c1");

        assertThat(decoded.getState().get(StateKeys.TOOL_APPROVAL))
                .isEqualTo(ToolDecision.APPROVE);
    }

    @Test
    void roundTrip_preservesMultimodalParts() throws Exception {
        Message user = Message.user(java.util.Arrays.asList(
                com.xmut.forma.pi.ai.message.ContentPart.text("extract"),
                com.xmut.forma.pi.ai.message.ContentPart.imageUrl("data:image/png;base64,abc", "high")));

        Map<String, Object> values = new HashMap<>();
        values.put(StateKeys.MESSAGES, Collections.singletonList(user));
        GraphState state = GraphState.create(values);

        Checkpoint original = new Checkpoint(
                "cp-mm", "run-mm", 1, "agent", state, Instant.parse("2026-08-16T04:00:00Z"),
                Collections.emptyMap());

        Checkpoint decoded = codec.decode(codec.encode(original)).orElseThrow(AssertionError::new);
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) decoded.getState().get(StateKeys.MESSAGES);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).hasImagePart()).isTrue();
        assertThat(messages.get(0).getParts()).hasSize(2);
        assertThat(messages.get(0).getParts().get(1).getUrl()).isEqualTo("data:image/png;base64,abc");
    }

    @Test
    void decode_invalidJson_returnsEmpty() {
        assertThat(codec.decode("{not-json")).isEmpty();
        assertThat(codec.decode(null)).isEmpty();
    }

    @Test
    void decode_invalidToolApproval_returnsEmpty() throws Exception {
        ToolCallEntry call = new ToolCallEntry(
                "c1", "save", JsonNodeFactory.instance.objectNode());
        Map<String, Object> values = new HashMap<>();
        values.put(StateKeys.MESSAGES, Collections.singletonList(Message.assistant("x", null)));
        values.put(StateKeys.TOOL_CALLS, Collections.singletonList(call));
        values.put(StateKeys.TOOL_APPROVAL, ToolDecision.APPROVE);
        Checkpoint original = new Checkpoint(
                "cp-bad", "run-1", 1, "human", GraphState.create(values),
                Instant.parse("2026-08-06T04:00:00Z"), Collections.emptyMap());
        String json = codec.encode(original).replace("\"APPROVE\"", "\"NOT_A_DECISION\"");
        assertThat(codec.decode(json)).isEmpty();
    }
}

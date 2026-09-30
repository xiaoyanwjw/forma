package com.xmut.lims.pi.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallEntryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void equals_valueEqual_differentInstances() throws Exception {
        ObjectNode args = mapper.createObjectNode().put("question", "确认？");
        ToolCallEntry a = new ToolCallEntry("call-1", "ask_human", args);
        ToolCallEntry b = new ToolCallEntry("call-1", "ask_human",
                mapper.readTree(mapper.writeValueAsString(args)));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void messageEquals_includesToolCallsByValue() throws Exception {
        ObjectNode args = mapper.createObjectNode().put("q", 1);
        Message left = Message.assistant("plan", Collections.singletonList(
                new ToolCallEntry("call-1", "ask_human", args)));
        Message right = Message.assistant("plan", Collections.singletonList(
                new ToolCallEntry("call-1", "ask_human",
                        mapper.readTree(mapper.writeValueAsString(args)))));

        assertThat(left).isEqualTo(right);
    }
}

package com.xmut.forma.pi.ai.model;

import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextToolCallParserTest {

    private final TextToolCallParser parser = new TextToolCallParser();

    @Test
    void parse_action_and_json_input() {
        List<ToolCallEntry> calls = parser.parse(
                "Thought: x\nAction: search\nAction Input: {\"q\":\"lims\"}\n");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getToolName()).isEqualTo("search");
        assertThat(calls.get(0).getArguments().get("q").asText()).isEqualTo("lims");
        assertThat(calls.get(0).getArguments().isObject()).isTrue();
    }

    @Test
    void parse_none_or_finish_returns_empty() {
        assertThat(parser.parse("Action: none\n")).isEmpty();
        assertThat(parser.parse("Action: Finish\n")).isEmpty();
        assertThat(parser.parse("just text")).isEmpty();
    }

    @Test
    void parse_unwraps_double_encoded_json_string() {
        List<ToolCallEntry> calls = parser.parse(
                "Action: echo\nAction Input: \"{\\\"n\\\":1}\"\n");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getArguments().isObject()).isTrue();
        assertThat(calls.get(0).getArguments().get("n").asInt()).isEqualTo(1);
    }

    @Test
    void parse_multiple_actions_and_multiline_input() {
        List<ToolCallEntry> calls = parser.parse(
                "Action: search\nAction Input: {\n  \"q\": \"a\"\n}\n"
                        + "Action: echo\nAction Input: {\"t\":1}\n");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).getToolName()).isEqualTo("search");
        assertThat(calls.get(0).getArguments().get("q").asText()).isEqualTo("a");
        assertThat(calls.get(1).getToolName()).isEqualTo("echo");
        assertThat(calls.get(1).getArguments().get("t").asInt()).isEqualTo(1);
    }

    @Test
    void with_tool_prompt_merges_into_existing_system() {
        List<com.xmut.forma.pi.ai.message.Message> msgs = TextToolCallParser.withToolPrompt(
                java.util.Collections.singletonList(
                        com.xmut.forma.pi.ai.message.Message.builder()
                                .role("system").content("policy").build()),
                java.util.Collections.singletonList(
                        ToolSchema.builder().name("echo").build()));
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getRole()).isEqualTo("system");
        assertThat(msgs.get(0).getContent()).contains("policy");
        assertThat(msgs.get(0).getContent()).contains("Action:");
    }
}

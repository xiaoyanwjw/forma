package com.xmut.lims.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.lims.pi.ai.model.ModelResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStreamAssemblerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiStreamAssembler assembler = new OpenAiStreamAssembler(mapper, "fallback-m");

    @Test
    void concatenates_content_deltas() throws Exception {
        assertThat(assembler.accept(mapper.readTree(
                "{\"choices\":[{\"delta\":{\"content\":\"He\"}}]}"))).isEqualTo("He");
        assertThat(assembler.accept(mapper.readTree(
                "{\"choices\":[{\"delta\":{\"content\":\"llo\"},\"finish_reason\":\"stop\"}]}"))).isEqualTo("llo");

        ModelResponse done = assembler.build();
        assertThat(done.getContent()).isEqualTo("Hello");
        assertThat(done.getFinishReason()).isEqualTo("stop");
        assertThat(done.getToolCalls()).isEmpty();
    }

    @Test
    void assembles_tool_call_fragments_by_index() throws Exception {
        assembler.accept(mapper.readTree(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"echo\",\"arguments\":\"\"}}]}}]}"));
        assembler.accept(mapper.readTree(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"x\\\":1}\"}}]},\"finish_reason\":\"tool_calls\"}]}"));

        ModelResponse done = assembler.build();
        assertThat(done.getToolCalls()).hasSize(1);
        assertThat(done.getToolCalls().get(0).getId()).isEqualTo("call_1");
        assertThat(done.getToolCalls().get(0).getToolName()).isEqualTo("echo");
        assertThat(done.getToolCalls().get(0).getArguments().get("x").asInt()).isEqualTo(1);
        assertThat(done.getFinishReason()).isEqualTo("tool_calls");
    }

    @Test
    void empty_content_delta_returns_null() throws Exception {
        assertThat(assembler.accept(mapper.readTree(
                "{\"choices\":[{\"delta\":{}}]}"))).isNull();
    }
}

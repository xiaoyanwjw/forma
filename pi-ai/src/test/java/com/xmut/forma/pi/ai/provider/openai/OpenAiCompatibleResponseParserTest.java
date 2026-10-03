package com.xmut.forma.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.exception.PiModelIoException;
import com.xmut.forma.pi.ai.model.ModelResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleResponseParserTest {

    private final OpenAiCompatibleResponseParser parser = new OpenAiCompatibleResponseParser(new ObjectMapper());

    @Test
    void parses_content_usage_and_tool_calls() {
        String raw = "{"
                + "\"model\":\"qwen-flash\","
                + "\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{"
                + "\"content\":\"\","
                + "\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"read_skill\",\"arguments\":\"{\\\"skill_id\\\":\\\"ecommerce-picklist\\\"}\"}}]"
                + "}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14}"
                + "}";
        ModelResponse r = parser.parse(raw, "fallback");
        assertThat(r.getModelVersion()).isEqualTo("qwen-flash");
        assertThat(r.getFinishReason()).isEqualTo("tool_calls");
        assertThat(r.getTotalTokens()).isEqualTo(14);
        assertThat(r.getToolCalls()).hasSize(1);
        assertThat(r.getToolCalls().get(0).getToolName()).isEqualTo("read_skill");
        assertThat(r.getToolCalls().get(0).getArguments().path("skill_id").asText()).isEqualTo("ecommerce-picklist");
    }

    @Test
    void empty_body_fails() {
        assertThatThrownBy(() -> parser.parse("  ", "m")).isInstanceOf(PiModelIoException.class);
    }
}

package com.xmut.lims.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.lims.pi.ai.message.ContentPart;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatiblePayloadBuilderTest {

    private final OpenAiCompatiblePayloadBuilder builder = new OpenAiCompatiblePayloadBuilder(new ObjectMapper());

    @Test
    void text_only_uses_string_content() throws Exception {
        ModelDescriptor desc = ModelDescriptor.builder()
                .useCase("pi.default")
                .provider("dashscope")
                .model("qwen-flash")
                .build();
        String json = builder.build(ModelRequest.builder()
                .messages(Collections.singletonList(Message.user("hello")))
                .build(), desc, false);
        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(root.path("model").asText()).isEqualTo("qwen-flash");
        assertThat(root.path("stream").asBoolean()).isFalse();
        assertThat(root.path("messages").get(0).path("content").isTextual()).isTrue();
        assertThat(root.path("messages").get(0).path("content").asText()).isEqualTo("hello");
    }

    @Test
    void image_parts_use_content_array() throws Exception {
        Message user = Message.user(Arrays.asList(
                ContentPart.text("ocr"),
                ContentPart.imageUrl("https://example/a.png", "high")));
        String json = builder.build(ModelRequest.builder()
                .messages(Collections.singletonList(user))
                .useCase(InMemoryModelCatalog.CERTIFICATE_OCR_USE_CASE)
                .build(), InMemoryModelCatalog.certificateOcrDescriptor(), false);
        JsonNode content = new ObjectMapper().readTree(json).path("messages").get(0).path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(1).path("type").asText()).isEqualTo("image_url");
        assertThat(content.get(1).path("image_url").path("url").asText()).isEqualTo("https://example/a.png");
        assertThat(content.get(1).path("image_url").path("detail").asText()).isEqualTo("high");
    }

    @Test
    void includes_tools_when_present() throws Exception {
        ModelDescriptor desc = ModelDescriptor.builder()
                .useCase("pi.default")
                .provider("dashscope")
                .model("qwen-flash")
                .supportsNativeToolCalling(true)
                .build();
        ToolSchema tool = ToolSchema.builder().name("read_skill").description("load skill").build();
        String json = builder.build(ModelRequest.builder()
                .messages(Collections.singletonList(Message.user("hi")))
                .tools(Collections.singletonList(tool))
                .build(), desc, false);
        JsonNode tools = new ObjectMapper().readTree(json).path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.get(0).path("function").path("name").asText()).isEqualTo("read_skill");
    }

    @Test
    void includes_thinking_mode_when_descriptor_sets_it() throws Exception {
        ModelDescriptor desc = InMemoryModelCatalog.testStandardSchemaDescriptor();
        String json = builder.build(ModelRequest.builder()
                .messages(Collections.singletonList(Message.user("hi")))
                .useCase(InMemoryModelCatalog.TEST_STANDARD_SCHEMA_USE_CASE)
                .build(), desc, false);
        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(root.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(root.path("max_tokens").asInt()).isEqualTo(16_000);
    }
}

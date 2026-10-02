package com.xmut.lims.pi.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolDefinitionJsonLoaderTest {

    @Test
    void load_reads_id_handlerClass_and_parameters() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(resolver);
        ToolDefinition d = defs.stream().filter(x -> "demo_echo".equals(x.getId())).findFirst().get();
        assertEquals("com.example.DemoEchoHandler", d.getHandlerClass());
        assertNotNull(d.getSchema());
        assertEquals("demo_echo", d.schemaOrDefault().getName());
    }

    @Test
    void parse_rejects_missing_id() {
        assertParseRejected("{ \"handlerClass\": \"com.example.DemoEchoHandler\", \"schema\": {} }", "id");
    }

    @Test
    void parse_rejects_missing_handlerClass() {
        assertParseRejected("{ \"id\": \"orphan\", \"schema\": { \"name\": \"orphan\" } }", "handlerClass");
    }

    @Test
    void parse_rejects_blank_handlerClass() {
        assertParseRejected(
                "{ \"id\": \"orphan\", \"handlerClass\": \"  \", \"schema\": { \"name\": \"orphan\" } }",
                "handlerClass");
    }

    @Test
    void parse_rejects_missing_schema_and_parameters() {
        assertParseRejected("{ \"id\": \"orphan\", \"handlerClass\": \"com.example.DemoEchoHandler\" }",
                "schema");
    }

    @Test
    void parse_accepts_top_level_parameters_without_schema_object() throws Exception {
        Resource resource = jsonResource(
                "{ \"id\": \"params_only\", \"handlerClass\": \"com.example.DemoEchoHandler\","
                        + " \"parameters\": { \"type\": \"object\" } }");
        ToolDefinition def = ToolDefinitionJsonLoader.parse(resource);
        assertThat(def.getId()).isEqualTo("params_only");
        assertThat(def.getHandlerClass()).isEqualTo("com.example.DemoEchoHandler");
        assertThat(def.getSchema()).isNotNull();
        assertThat(def.getSchema().getParametersSchema()).isNotNull();
        assertThat(def.getSchema().getParametersSchema().get("type").asText()).isEqualTo("object");
    }

    private static void assertParseRejected(String json, String fieldHint) {
        assertThatThrownBy(() -> ToolDefinitionJsonLoader.parse(jsonResource(json)))
                .isInstanceOfAny(ToolValidationException.class, IllegalArgumentException.class)
                .hasMessageContaining(fieldHint);
    }

    private static Resource jsonResource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "fixture.tool.json");
    }
}

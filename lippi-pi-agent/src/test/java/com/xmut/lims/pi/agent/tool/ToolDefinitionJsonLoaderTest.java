package com.xmut.lims.pi.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;

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
}

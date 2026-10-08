package com.xmut.forma.extension.tool.product.research;

import com.xmut.forma.pi.agent.tool.ToolDefinition;
import com.xmut.forma.pi.agent.tool.ToolDefinitionJsonLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchProductsToolJsonTest {

    @Test
    void research_products_json_handlerClass_points_at_extension_package() throws Exception {
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(new PathMatchingResourcePatternResolver());
        ToolDefinition d = defs.stream()
                .filter(t -> "research_products".equals(t.getId()))
                .findFirst()
                .get();
        assertEquals(
                "com.xmut.forma.extension.tool.product.research.ResearchProductsToolHandler",
                d.getHandlerClass());
        assertTrue(d.getSchema().getParametersSchema().path("properties").has("candidates"));
        assertTrue(d.getSchema().getParametersSchema().path("properties").has("deepFetch"));
        assertTrue(d.getSchema().getParametersSchema().path("properties").has("candidatesPath"));
    }
}

package com.xmut.forma.extension.tool.product.recall;

import com.xmut.forma.pi.agent.tool.ToolDefinition;
import com.xmut.forma.pi.agent.tool.ToolDefinitionJsonLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecallProductsToolJsonTest {

    @Test
    void recall_products_json_handlerClass_points_at_extension_package() throws Exception {
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(new PathMatchingResourcePatternResolver());
        ToolDefinition d = defs.stream()
                .filter(t -> "recall_products".equals(t.getId()))
                .findFirst()
                .get();
        assertEquals(
                "com.xmut.forma.extension.tool.product.recall.RecallProductsToolHandler",
                d.getHandlerClass());
        assertTrue(d.getSchema().getParametersSchema().path("properties").has("paste"));
        assertTrue(d.getSchema().getParametersSchema().path("properties").has("topic"));
    }
}

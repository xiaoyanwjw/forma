package com.xmut.ebus.extension.tool.sku;

import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.ToolDefinitionJsonLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchSkuToolJsonTest {

    @Test
    void search_sku_json_handlerClass_points_at_extension_package() throws Exception {
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(new PathMatchingResourcePatternResolver());
        ToolDefinition d = defs.stream().filter(t -> "search_sku".equals(t.getId())).findFirst().get();
        assertEquals("com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler", d.getHandlerClass());
    }
}

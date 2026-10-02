package com.xmut.ebus.extension.config;

import com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler;
import com.xmut.ebus.extension.tool.sku.port.SkuSearchProperties;
import com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.ToolDefinitionJsonLoader;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbusPiToolCatalogConfigurationTest {

    @Test
    void overlayFactory_resolvesSkuRerankAndPiDefault() {
        ModelCatalog catalog = EbusModelCatalogAutoConfiguration.overlayWithSkuRerank(new SkuSearchProperties());
        ModelDescriptor rerank = catalog.resolve("ebus.sku.rerank");
        assertNotNull(rerank);
        assertEquals("ebus.sku.rerank", rerank.getUseCase());
        assertEquals(InMemoryModelCatalog.defaultChatDescriptor().getProvider(), rerank.getProvider());
        assertEquals(InMemoryModelCatalog.defaultChatDescriptor().getModel(), rerank.getModel());
        assertEquals(0.0, rerank.getTemperature());
        assertEquals(512, rerank.getMaxTokens());
        assertNotNull(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE));
        assertNotNull(catalog.resolve("ebus.xhs.rerank"));
        assertEquals("ebus.xhs.rerank", catalog.resolve("ebus.xhs.rerank").getUseCase());
    }

    @Test
    void loader_scans_sku_and_xhs_tool_json() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(resolver);
        assertEquals("com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler",
                handlerClassOf(defs, "search_sku"));
        assertEquals("com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler",
                handlerClassOf(defs, "search_xhs_note"));
        assertEquals("com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler",
                handlerClassOf(defs, "fetch_xhs_note"));
        assertTrue(handlerClassOf(defs, "search_sku") != null
                && defs.stream().anyMatch(d -> "search_sku".equals(d.getId())
                && d.getSchema() != null
                && d.getSchema().getParametersSchema() != null
                && d.getSchema().getParametersSchema().path("properties").has("query")));
    }

    @Test
    void liveConfigBean_resolvesSkuRerank() {
        new ApplicationContextRunner()
                .withUserConfiguration(EbusModelCatalogAutoConfiguration.class)
                .run(context -> {
                    ModelCatalog catalog = context.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("ebus.sku.rerank"));
                    assertNotNull(catalog.resolve("ebus.xhs.rerank"));
                    assertNotNull(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE));
                });
    }

    private static String handlerClassOf(List<ToolDefinition> defs, String id) {
        for (int i = 0; i < defs.size(); i++) {
            ToolDefinition def = defs.get(i);
            if (def != null && id.equals(def.getId())) {
                return def.getHandlerClass();
            }
        }
        return null;
    }
}

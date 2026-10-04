package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.sku.SearchSkuToolHandler;
import com.xmut.forma.extension.tool.sku.port.SkuSearchProperties;
import com.xmut.forma.extension.tool.view.RenderViewToolHandler;
import com.xmut.forma.extension.tool.xhs.FetchXhsNoteToolHandler;
import com.xmut.forma.extension.tool.xhs.SearchXhsNoteToolHandler;
import com.xmut.forma.pi.agent.tool.ToolDefinition;
import com.xmut.forma.pi.agent.tool.ToolDefinitionJsonLoader;
import com.xmut.forma.pi.ai.model.InMemoryModelCatalog;
import com.xmut.forma.pi.ai.model.ModelCatalog;
import com.xmut.forma.pi.ai.model.ModelDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormaPiToolCatalogConfigurationTest {

    @Test
    void overlayFactory_resolvesSkuRerankAndPiDefault() {
        ModelCatalog catalog = FormaModelCatalogAutoConfiguration.overlayWithSkuRerank(new SkuSearchProperties());
        ModelDescriptor rerank = catalog.resolve("forma.sku.rerank");
        assertNotNull(rerank);
        assertEquals("forma.sku.rerank", rerank.getUseCase());
        assertEquals(InMemoryModelCatalog.defaultChatDescriptor().getProvider(), rerank.getProvider());
        assertEquals(InMemoryModelCatalog.defaultChatDescriptor().getModel(), rerank.getModel());
        assertEquals(0.0, rerank.getTemperature());
        assertEquals(512, rerank.getMaxTokens());
        assertNotNull(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE));
        assertNotNull(catalog.resolve("forma.xhs.rerank"));
        assertEquals("forma.xhs.rerank", catalog.resolve("forma.xhs.rerank").getUseCase());
        ModelDescriptor excerpt = catalog.resolve("forma.tech.excerpt");
        assertNotNull(excerpt);
        assertEquals("forma.tech.excerpt", excerpt.getUseCase());
        assertEquals(0.0, excerpt.getTemperature());
        assertEquals(1024, excerpt.getMaxTokens());
    }

    @Test
    void loader_scans_sku_and_xhs_tool_json() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(resolver);
        assertEquals("com.xmut.forma.extension.tool.sku.SearchSkuToolHandler",
                handlerClassOf(defs, "search_sku"));
        assertEquals("com.xmut.forma.extension.tool.xhs.SearchXhsNoteToolHandler",
                handlerClassOf(defs, "search_xhs_note"));
        assertEquals("com.xmut.forma.extension.tool.xhs.FetchXhsNoteToolHandler",
                handlerClassOf(defs, "fetch_xhs_note"));
        assertEquals("com.xmut.forma.extension.tool.web.FetchWebPageToolHandler",
                handlerClassOf(defs, "fetch_web_page"));
        assertEquals("com.xmut.forma.extension.tool.tech.ExcerptChunksToolHandler",
                handlerClassOf(defs, "excerpt_chunks"));
        assertEquals(RenderViewToolHandler.class.getName(), handlerClassOf(defs, "render_view"));
        assertTrue(defs.stream().anyMatch(d -> "render_view".equals(d.getId())
                && d.getSchema() != null
                && d.getSchema().getParametersSchema() != null));
        assertTrue(handlerClassOf(defs, "search_sku") != null
                && defs.stream().anyMatch(d -> "search_sku".equals(d.getId())
                && d.getSchema() != null
                && d.getSchema().getParametersSchema() != null
                && d.getSchema().getParametersSchema().path("properties").has("query")));
    }

    @Test
    void liveConfigBean_resolvesSkuRerank() {
        new ApplicationContextRunner()
                .withUserConfiguration(FormaModelCatalogAutoConfiguration.class)
                .run(context -> {
                    ModelCatalog catalog = context.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("forma.sku.rerank"));
                    assertNotNull(catalog.resolve("forma.xhs.rerank"));
                    assertNotNull(catalog.resolve("forma.tech.excerpt"));
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

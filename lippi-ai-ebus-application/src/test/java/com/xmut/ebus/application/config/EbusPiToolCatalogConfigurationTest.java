package com.xmut.ebus.application.config;

import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteFetchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteFetchPort;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchPort;
import com.xmut.lims.pi.agent.tool.ToolDefinition;
import com.xmut.lims.pi.agent.tool.ToolDefinitionJsonLoader;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbusPiToolCatalogConfigurationTest {

    @Test
    void overlayFactory_resolvesSkuRerankAndPiDefault() {
        ModelCatalog catalog = PiToolCatalogConfiguration.overlayWithSkuRerank(new SkuSearchProperties());
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
        assertEquals("com.xmut.ebus.application.business.agent.tool.sku.SearchSkuToolHandler",
                handlerClassOf(defs, "search_sku"));
        assertEquals("com.xmut.ebus.application.business.agent.tool.xhs.SearchXhsNoteToolHandler",
                handlerClassOf(defs, "search_xhs_note"));
        assertEquals("com.xmut.ebus.application.business.agent.tool.xhs.FetchXhsNoteToolHandler",
                handlerClassOf(defs, "fetch_xhs_note"));
        assertTrue(handlerClassOf(defs, "search_sku") != null
                && defs.stream().anyMatch(d -> "search_sku".equals(d.getId())
                && d.getSchema() != null
                && d.getSchema().getParametersSchema() != null
                && d.getSchema().getParametersSchema().path("properties").has("query")));
    }

    @Test
    void xhsNoteSearchPort_apify_binds_apify_client_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-search.client=apify")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof ApifyXhsNoteSearchClient);
                });
    }

    @Test
    void xhsNoteFetchPort_apify_binds_apify_client_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-fetch.client=apify")
                .run(context -> {
                    XhsNoteFetchPort port = context.getBean(XhsNoteFetchPort.class);
                    assertTrue(port instanceof ApifyXhsNoteFetchClient);
                });
    }

    @Test
    void xhsNoteFetchPort_mock_binds_mock_client() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-fetch.client=mock")
                .run(context -> {
                    XhsNoteFetchPort port = context.getBean(XhsNoteFetchPort.class);
                    assertTrue(port instanceof MockXhsNoteFetchClient);
                });
    }

    @Test
    void xhsNoteSearchPort_mock_binds_mock_client() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-search.client=mock")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof MockXhsNoteSearchClient);
                });
    }

    @Test
    void liveConfigBean_resolvesSkuRerank() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
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

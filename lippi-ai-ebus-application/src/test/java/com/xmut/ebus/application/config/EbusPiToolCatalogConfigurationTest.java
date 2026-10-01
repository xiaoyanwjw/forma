package com.xmut.ebus.application.config;

import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.ApifyXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.MockXhsNoteSearchClient;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchPort;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
    void xhsNoteSearchPort_apify_binds_apify_client_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
                .withBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class, EbusPiToolCatalogConfigurationTest::sealedSkillCatalog)
                .withPropertyValues("ebus.xhs-note-search.client=apify")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof ApifyXhsNoteSearchClient);
                });
    }

    @Test
    void xhsNoteSearchPort_mock_binds_mock_client() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
                .withBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class, EbusPiToolCatalogConfigurationTest::sealedSkillCatalog)
                .withPropertyValues("ebus.xhs-note-search.client=mock")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof MockXhsNoteSearchClient);
                });
    }

    private static com.xmut.lims.pi.agent.skill.SkillCatalog sealedSkillCatalog() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.sealBootstrap();
        return skills;
    }

    @Test
    void liveConfigBean_resolvesSkuRerank() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
                .withBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class, EbusPiToolCatalogConfigurationTest::sealedSkillCatalog)
                .run(context -> {
                    ModelCatalog catalog = context.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("ebus.sku.rerank"));
                    assertNotNull(catalog.resolve("ebus.xhs.rerank"));
                    assertNotNull(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE));
                });
    }
}

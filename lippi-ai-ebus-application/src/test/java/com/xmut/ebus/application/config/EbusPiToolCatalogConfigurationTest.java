package com.xmut.ebus.application.config;

import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void liveConfigBean_resolvesSkuRerank() {
        new ApplicationContextRunner()
                .withUserConfiguration(PiToolCatalogConfiguration.class)
                .withBean(com.xmut.lims.pi.agent.skill.SkillCatalog.class, () -> {
                    InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
                    skills.sealBootstrap();
                    return skills;
                })
                .run(context -> {
                    ModelCatalog catalog = context.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("ebus.sku.rerank"));
                    assertNotNull(catalog.resolve("ebus.xhs.rerank"));
                    assertNotNull(catalog.resolve(InMemoryModelCatalog.DEFAULT_USE_CASE));
                });
    }
}

package com.xmut.forma.extension.config;

import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.ai.model.ModelCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FormaModelCatalogAutoConfigurationTest {

    @Test
    void modelCatalog_resolves_sku_and_xhs_rerank_use_cases() {
        new ApplicationContextRunner()
                .withBean(SkillCatalog.class, InMemorySkillCatalog::new)
                .withConfiguration(AutoConfigurations.of(
                        FormaModelCatalogAutoConfiguration.class,
                        SkuToolsConfiguration.class,
                        XhsToolsConfiguration.class))
                .run(ctx -> {
                    ModelCatalog catalog = ctx.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("forma.sku.rerank"));
                    assertNotNull(catalog.resolve("forma.xhs.rerank"));
                    assertNotNull(catalog.resolve("forma.tech.excerpt"));
                });
    }
}

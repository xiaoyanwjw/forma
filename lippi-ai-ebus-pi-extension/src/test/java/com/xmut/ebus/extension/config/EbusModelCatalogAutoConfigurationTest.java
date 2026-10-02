package com.xmut.ebus.extension.config;

import com.xmut.lims.pi.ai.model.ModelCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EbusModelCatalogAutoConfigurationTest {

    @Test
    void modelCatalog_resolves_sku_and_xhs_rerank_use_cases() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EbusModelCatalogAutoConfiguration.class,
                        SkuToolsConfiguration.class,
                        XhsToolsConfiguration.class))
                .run(ctx -> {
                    ModelCatalog catalog = ctx.getBean(ModelCatalog.class);
                    assertNotNull(catalog.resolve("ebus.sku.rerank"));
                    assertNotNull(catalog.resolve("ebus.xhs.rerank"));
                });
    }
}

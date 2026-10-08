package com.xmut.forma.extension.config.product;

import com.xmut.forma.extension.config.common.ExcerptToolsConfiguration;
import com.xmut.forma.extension.config.common.PageIngestToolsConfiguration;
import com.xmut.forma.extension.config.common.WebFetchToolsConfiguration;
import com.xmut.forma.extension.tool.product.recall.RecallProductsToolHandler;
import com.xmut.forma.extension.tool.product.recall.source.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductToolsConfigurationTest {

    @Test
    void registers_handler_and_apify_port_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        WebFetchToolsConfiguration.class,
                        ExcerptToolsConfiguration.class,
                        PageIngestToolsConfiguration.class,
                        ProductToolsConfiguration.class)
                .withPropertyValues("forma.product-launch-search.apify.token=")
                .run(context -> {
                    assertTrue(context.getBean(ProductLaunchSearchPort.class)
                            instanceof ApifyProductHuntSearchClient);
                    assertTrue(context.getBean(RecallProductsToolHandler.class) != null);
                    ProductLaunchSearchProperties props = context.getBean(ProductLaunchSearchProperties.class);
                    assertEquals(ProductLaunchSearchProperties.DEFAULT_ACTOR_ID, props.getApify().getActorId());
                });
    }
}

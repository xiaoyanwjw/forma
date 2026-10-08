package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.ph.SearchProductLaunchesToolHandler;
import com.xmut.forma.extension.tool.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductLaunchToolsConfigurationTest {

    @Test
    void registers_handler_and_apify_port_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductLaunchToolsConfiguration.class)
                .withPropertyValues("forma.product-launch-search.apify.token=")
                .run(context -> {
                    assertTrue(context.getBean(ProductLaunchSearchPort.class)
                            instanceof ApifyProductHuntSearchClient);
                    assertTrue(context.getBean(SearchProductLaunchesToolHandler.class) != null);
                    ProductLaunchSearchProperties props = context.getBean(ProductLaunchSearchProperties.class);
                    assertEquals(ProductLaunchSearchProperties.DEFAULT_ACTOR_ID, props.getApify().getActorId());
                });
    }
}

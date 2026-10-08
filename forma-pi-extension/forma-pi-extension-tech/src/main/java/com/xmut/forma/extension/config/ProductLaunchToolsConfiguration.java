package com.xmut.forma.extension.config;

import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.extension.tool.ph.SearchProductLaunchesToolHandler;
import com.xmut.forma.extension.tool.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code search_product_launches}. Schema:
 * {@code tools/ph/search_product_launches.tool.json}.
 *
 * <p>Missing {@code APIFY_TOKEN} fails at call time with a structured tool error
 * (no mock inventions).
 */
@Configuration
@EnableConfigurationProperties(ProductLaunchSearchProperties.class)
public class ProductLaunchToolsConfiguration {

    @Bean
    public ProductLaunchSearchPort productLaunchSearchPort(ProductLaunchSearchProperties props) {
        return new ApifyProductHuntSearchClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public SearchProductLaunchesToolHandler searchProductLaunchesToolHandler(
            ProductLaunchSearchPort productLaunchSearchPort,
            ProductLaunchSearchProperties props) {
        return new SearchProductLaunchesToolHandler(productLaunchSearchPort, props);
    }
}

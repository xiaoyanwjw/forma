package com.xmut.forma.extension.config;

import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchProperties;
import com.xmut.forma.extension.tool.product.recall.RecallProductsToolHandler;
import com.xmut.forma.extension.tool.product.research.ResearchProductsToolHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code recall_products} and {@code research_products}. Schemas:
 * {@code tools/product/recall_products.tool.json},
 * {@code tools/product/research_products.tool.json}.
 *
 * <p>Missing {@code APIFY_TOKEN} fails at call time with a structured tool error
 * (no mock inventions). Paste path skips Apify. Research uses {@link PageFetchPort} only.
 */
@Configuration
@EnableConfigurationProperties(ProductLaunchSearchProperties.class)
public class ProductLaunchToolsConfiguration {

    @Bean
    public ProductLaunchSearchPort productLaunchSearchPort(ProductLaunchSearchProperties props) {
        return new ApifyProductHuntSearchClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public RecallProductsToolHandler recallProductsToolHandler(
            ProductLaunchSearchPort productLaunchSearchPort,
            ProductLaunchSearchProperties props) {
        return new RecallProductsToolHandler(productLaunchSearchPort, props);
    }

    @Bean
    public ResearchProductsToolHandler researchProductsToolHandler(PageFetchPort pageFetchPort) {
        return new ResearchProductsToolHandler(pageFetchPort);
    }
}

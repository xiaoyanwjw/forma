package com.xmut.forma.extension.config.product;

import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.product.ingest.IngestCompetitorToolHandler;
import com.xmut.forma.extension.tool.product.recall.RecallProductsToolHandler;
import com.xmut.forma.extension.tool.product.recall.source.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchProperties;
import com.xmut.forma.extension.tool.product.research.ResearchProductsToolHandler;
import com.xmut.forma.extension.tool.product.view.TechBriefingViewEnricher;
import com.xmut.forma.extension.tool.product.view.TechCompetitorViewEnricher;
import com.xmut.forma.extension.tool.view.ViewEnricher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers product-domain business tools and ViewEnrichers:
 * {@code recall_products}, {@code research_products}, {@code ingest_competitor}.
 *
 * <p>Missing {@code APIFY_TOKEN} fails at call time with a structured tool error
 * (no mock inventions). Paste path skips Apify. Research uses {@link PageFetchPort} only.
 */
@Configuration
@EnableConfigurationProperties(ProductLaunchSearchProperties.class)
public class ProductToolsConfiguration {

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

    @Bean
    public IngestCompetitorToolHandler ingestCompetitorToolHandler(PageIngestService pageIngestService) {
        return new IngestCompetitorToolHandler(pageIngestService);
    }

    @Bean
    public ViewEnricher techCompetitorViewEnricher() {
        return new TechCompetitorViewEnricher();
    }

    @Bean
    public ViewEnricher techBriefingViewEnricher() {
        return new TechBriefingViewEnricher();
    }
}

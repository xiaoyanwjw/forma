package com.xmut.forma.extension.config;

import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.extension.tool.sku.SearchSkuToolHandler;
import com.xmut.forma.extension.tool.sku.client.ApifyTaobaoSkuSearchClient;
import com.xmut.forma.extension.tool.sku.client.FallbackSkuSearchClient;
import com.xmut.forma.extension.tool.sku.client.MockSkuSearchClient;
import com.xmut.forma.extension.tool.sku.port.SkuSearchPort;
import com.xmut.forma.extension.tool.sku.port.SkuSearchProperties;
import com.xmut.forma.extension.tool.sku.search.ModelSkuReranker;
import com.xmut.forma.extension.tool.sku.search.SkuReranker;
import com.xmut.forma.extension.tool.sku.search.SkuSearcher;
import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.pi.ai.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers SKU search Handler and Port/Searcher. Schema comes from {@code tools/sku/*.tool.json}.
 */
@Configuration
@EnableConfigurationProperties(SkuSearchProperties.class)
public class SkuToolsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkuToolsConfiguration.class);

    @Bean
    public SkuSearchPort skuSearchPort(SkuSearchProperties props) {
        MockSkuSearchClient mock = new MockSkuSearchClient();
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return mock;
        }
        String actorId = props.getApify().getActorId();
        String token = props.getApify().getToken();
        if (token == null || token.trim().isEmpty()) {
            LoggerUtils.error(
                    log,
                    SkuToolsConfiguration.class,
                    "skuSearchPort",
                    "missing_token",
                    NameValue.create("client", "apify"),
                    NameValue.create("actorId", actorId),
                    NameValue.create("queryLen", 0),
                    NameValue.create("hitCount", 0));
            return mock;
        }
        ApifyTaobaoSkuSearchClient apify =
                new ApifyTaobaoSkuSearchClient(props, new ApifyOkHttpTransport());
        return new FallbackSkuSearchClient(apify, mock, actorId);
    }

    @Bean
    public SkuReranker skuReranker(ObjectProvider<ModelProvider> models, SkuSearchProperties props) {
        ModelProvider mp = models.getIfAvailable();
        if (mp == null) {
            return SkuReranker.identity();
        }
        return new ModelSkuReranker(mp, props);
    }

    @Bean
    public SkuSearcher skuSearcher(SkuSearchPort port, SkuSearchProperties props, SkuReranker reranker) {
        return new SkuSearcher(port, props, reranker);
    }

    @Bean
    public SearchSkuToolHandler searchSkuToolHandler(SkuSearcher skuSearcher) {
        return new SearchSkuToolHandler(skuSearcher);
    }
}

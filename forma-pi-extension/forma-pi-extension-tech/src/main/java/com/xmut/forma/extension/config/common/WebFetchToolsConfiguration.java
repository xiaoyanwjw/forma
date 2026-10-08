package com.xmut.forma.extension.config.common;

import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.web.fetch.client.ApifyWebsiteContentCrawlerClient;
import com.xmut.forma.extension.tool.common.web.fetch.client.MockWebFetchClient;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes 抓取 {@link PageFetchPort} for business tools only (not Agent-visible).
 */
@Configuration
@EnableConfigurationProperties(WebFetchProperties.class)
public class WebFetchToolsConfiguration {

    @Bean
    public PageFetchPort pageFetchPort(WebFetchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockWebFetchClient();
        }
        String token = props.getApify().getToken();
        if (token == null || token.trim().isEmpty()) {
            return new MockWebFetchClient();
        }
        return new ApifyWebsiteContentCrawlerClient(props, new ApifyOkHttpTransport());
    }
}

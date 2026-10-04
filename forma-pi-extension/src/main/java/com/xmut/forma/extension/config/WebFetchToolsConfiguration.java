package com.xmut.forma.extension.config;

import com.xmut.forma.extension.common.ApifyOkHttpTransport;
import com.xmut.forma.extension.tool.web.FetchWebPageToolHandler;
import com.xmut.forma.extension.tool.web.client.ApifyWebsiteContentCrawlerClient;
import com.xmut.forma.extension.tool.web.client.MockWebFetchClient;
import com.xmut.forma.extension.tool.web.port.WebFetchPort;
import com.xmut.forma.extension.tool.web.port.WebFetchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code fetch_web_page}. Schema comes from {@code tools/web/fetch_web_page.tool.json}.
 */
@Configuration
@EnableConfigurationProperties(WebFetchProperties.class)
public class WebFetchToolsConfiguration {

    @Bean
    public WebFetchPort webFetchPort(WebFetchProperties props) {
        if (!"apify".equalsIgnoreCase(props.getClient())) {
            return new MockWebFetchClient();
        }
        return new ApifyWebsiteContentCrawlerClient(props, new ApifyOkHttpTransport());
    }

    @Bean
    public FetchWebPageToolHandler fetchWebPageToolHandler(WebFetchPort webFetchPort) {
        return new FetchWebPageToolHandler(webFetchPort);
    }
}

package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.web.client.ApifyWebsiteContentCrawlerClient;
import com.xmut.forma.extension.tool.web.client.MockWebFetchClient;
import com.xmut.forma.extension.tool.web.port.WebFetchPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFetchToolsConfigurationTest {

    @Test
    void webFetchPort_apifyWithBlankToken_bindsMock() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=")
                .run(context -> {
                    WebFetchPort port = context.getBean(WebFetchPort.class);
                    assertTrue(port instanceof MockWebFetchClient);
                });
    }

    @Test
    void webFetchPort_apifyWithWhitespaceToken_bindsMock() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=   ")
                .run(context -> {
                    WebFetchPort port = context.getBean(WebFetchPort.class);
                    assertTrue(port instanceof MockWebFetchClient);
                });
    }

    @Test
    void webFetchPort_apifyWithToken_bindsApifyClient() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=test-token")
                .run(context -> {
                    WebFetchPort port = context.getBean(WebFetchPort.class);
                    assertTrue(port instanceof ApifyWebsiteContentCrawlerClient);
                });
    }
}

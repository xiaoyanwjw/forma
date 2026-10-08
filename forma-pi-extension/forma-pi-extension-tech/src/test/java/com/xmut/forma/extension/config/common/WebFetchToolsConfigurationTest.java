package com.xmut.forma.extension.config.common;

import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.web.fetch.client.ApifyWebsiteContentCrawlerClient;
import com.xmut.forma.extension.tool.common.web.fetch.client.MockWebFetchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFetchToolsConfigurationTest {

    @Test
    void pageFetchPort_apifyWithBlankToken_bindsMock() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=")
                .run(context -> {
                    PageFetchPort port = context.getBean(PageFetchPort.class);
                    assertTrue(port instanceof MockWebFetchClient);
                });
    }

    @Test
    void pageFetchPort_apifyWithWhitespaceToken_bindsMock() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=   ")
                .run(context -> {
                    PageFetchPort port = context.getBean(PageFetchPort.class);
                    assertTrue(port instanceof MockWebFetchClient);
                });
    }

    @Test
    void pageFetchPort_apifyWithToken_bindsApifyClient() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebFetchToolsConfiguration.class)
                .withPropertyValues("forma.web-fetch.client=apify", "forma.web-fetch.apify.token=test-token")
                .run(context -> {
                    PageFetchPort port = context.getBean(PageFetchPort.class);
                    assertTrue(port instanceof ApifyWebsiteContentCrawlerClient);
                });
    }
}

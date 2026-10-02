package com.xmut.ebus.extension.tool.xhs;

import com.xmut.ebus.extension.config.XhsToolsConfiguration;
import com.xmut.ebus.extension.tool.xhs.client.ApifyXhsNoteFetchClient;
import com.xmut.ebus.extension.tool.xhs.client.ApifyXhsNoteSearchClient;
import com.xmut.ebus.extension.tool.xhs.client.MockXhsNoteFetchClient;
import com.xmut.ebus.extension.tool.xhs.client.MockXhsNoteSearchClient;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteFetchPort;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XhsToolsConfigurationTest {

    @Test
    void xhsNoteSearchPort_apify_binds_apify_client_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-search.client=apify")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof ApifyXhsNoteSearchClient);
                });
    }

    @Test
    void xhsNoteFetchPort_apify_binds_apify_client_even_without_token() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-fetch.client=apify")
                .run(context -> {
                    XhsNoteFetchPort port = context.getBean(XhsNoteFetchPort.class);
                    assertTrue(port instanceof ApifyXhsNoteFetchClient);
                });
    }

    @Test
    void xhsNoteFetchPort_mock_binds_mock_client() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-fetch.client=mock")
                .run(context -> {
                    XhsNoteFetchPort port = context.getBean(XhsNoteFetchPort.class);
                    assertTrue(port instanceof MockXhsNoteFetchClient);
                });
    }

    @Test
    void xhsNoteSearchPort_mock_binds_mock_client() {
        new ApplicationContextRunner()
                .withUserConfiguration(XhsToolsConfiguration.class)
                .withPropertyValues("ebus.xhs-note-search.client=mock")
                .run(context -> {
                    XhsNoteSearchPort port = context.getBean(XhsNoteSearchPort.class);
                    assertTrue(port instanceof MockXhsNoteSearchClient);
                });
    }
}

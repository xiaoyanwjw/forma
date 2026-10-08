package com.xmut.forma.extension.tool.common.web.crawl.port;

import com.xmut.forma.extension.tool.common.web.crawl.UnsupportedSiteCrawlPort;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SiteCrawlPort is the 爬取 atom placeholder — not wired to business tools yet.
 */
class SiteCrawlPortTest {

    @Test
    void stub_throwsUnsupported_andIsNotBusinessWired() {
        SiteCrawlPort port = new UnsupportedSiteCrawlPort();
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> port.crawl("https://example.com/", 3));
        assertTrue(ex.getMessage().contains("not wired"));
    }
}

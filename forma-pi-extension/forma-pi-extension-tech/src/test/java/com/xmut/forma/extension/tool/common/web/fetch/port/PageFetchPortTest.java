package com.xmut.forma.extension.tool.common.web.fetch.port;

import com.xmut.forma.extension.tool.web.client.MockWebFetchClient;
import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PageFetchPort is the 抓取 atom: one URL → one page, same as today's single-page fetch.
 */
class PageFetchPortTest {

    private static final String PAGE = "https://example.com/product";

    @Test
    void mockClient_isPageFetchPort_andMatchesSinglePageHit() {
        PageFetchPort port = new MockWebFetchClient();
        WebFetchHit hit = port.fetch(PAGE);

        assertEquals(PAGE, hit.getFinalUrl());
        assertEquals("Mock 科技页", hit.getTitle());
        assertTrue(hit.getText().length() >= 500);
        assertFalse(hit.isTruncated());
    }
}

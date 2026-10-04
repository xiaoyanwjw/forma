package com.xmut.forma.extension.tool.xhs.port;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XhsNoteFetchPropertiesTest {

    @Test
    void defaults_apify_and_default_actor() {
        XhsNoteFetchProperties p = new XhsNoteFetchProperties();
        assertEquals("apify", p.getClient());
        assertEquals("khadinakbar/xiaohongshu-note-detail-scraper", p.getApify().getActorId());
        assertEquals(120_000L, p.getApify().getTimeoutMs());
    }
}

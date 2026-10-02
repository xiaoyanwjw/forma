package com.xmut.ebus.extension.tool.xhs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XhsNoteSearchPropertiesTest {

    @Test
    void defaults_mock_and_default_actor() {
        XhsNoteSearchProperties p = new XhsNoteSearchProperties();
        assertEquals("mock", p.getClient());
        assertEquals("opspilot.cc/xiaohongshu-keyword-search-scraper", p.getApify().getActorId());
        assertTrue(p.getSearcher().isEnabled());
        assertEquals(40, p.getSearcher().getRerankPoolSize());
        assertEquals("ebus.xhs.rerank", p.getSearcher().getRerankUseCase());
    }
}

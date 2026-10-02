package com.xmut.ebus.extension.sku;

import com.xmut.ebus.extension.tool.sku.SkuSearchProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkuSearchPropertiesTest {

    @Test
    void defaults_to_mock_client_and_default_actor() {
        SkuSearchProperties p = new SkuSearchProperties();
        assertEquals("mock", p.getClient());
        assertEquals("zen-studio/taobao-search-scraper", p.getApify().getActorId());
        assertEquals(120_000L, p.getApify().getTimeoutMs());
    }
}

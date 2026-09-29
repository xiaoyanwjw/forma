package com.xmut.ebus.application.business.sku;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackSkuSearchClientTest {

    @Test
    void returns_primary_hits_when_search_succeeds() {
        SkuSearchHit apifyHit = new SkuSearchHit(
                "taobao_apify",
                "Apify 商品",
                "29.90",
                "香氛",
                "https://item.taobao.com/item.htm?id=1",
                "1");
        SkuSearchPort primary = (q, p, n) -> Collections.singletonList(apifyHit);
        AtomicInteger fallbackCalls = new AtomicInteger();
        SkuSearchPort mock = (q, p, n) -> {
            fallbackCalls.incrementAndGet();
            return Collections.emptyList();
        };
        FallbackSkuSearchClient fb = new FallbackSkuSearchClient(primary, mock);
        List<SkuSearchHit> hits = fb.search("香薰", "taobao_tbk", 5);
        assertEquals(1, hits.size());
        assertEquals("taobao_apify", hits.get(0).getPlatform());
        assertEquals("https://item.taobao.com/item.htm?id=1", hits.get(0).getDetailUrl());
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void falls_back_when_primary_throws() {
        SkuSearchPort primary = (q, p, n) -> {
            throw new IllegalStateException("boom");
        };
        SkuSearchPort mock = new MockSkuSearchClient();
        FallbackSkuSearchClient fb = new FallbackSkuSearchClient(primary, mock);
        List<SkuSearchHit> hits = fb.search("香薰", "taobao_tbk", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getDetailUrl().contains("mock.tbk.local"));
    }

    @Test
    void falls_back_when_primary_returns_empty() {
        SkuSearchPort primary = (q, p, n) -> Collections.emptyList();
        SkuSearchPort mock = new MockSkuSearchClient();
        FallbackSkuSearchClient fb = new FallbackSkuSearchClient(primary, mock);
        List<SkuSearchHit> hits = fb.search("香薰", "taobao_tbk", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getDetailUrl().contains("mock.tbk.local"));
    }
}

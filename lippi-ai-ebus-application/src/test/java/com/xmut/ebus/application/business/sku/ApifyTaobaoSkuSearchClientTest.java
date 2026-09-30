package com.xmut.ebus.application.business.sku;

import com.xmut.ebus.application.business.agent.tool.sku.ApifyActorTransport;
import com.xmut.ebus.application.business.agent.tool.sku.ApifyTaobaoSkuSearchClient;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchHit;
import com.xmut.ebus.application.business.agent.tool.sku.SkuSearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyTaobaoSkuSearchClientTest {

    @Test
    void search_maps_transport_payload() {
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) ->
                "[{\"title\":\"a\",\"price\":1,\"url\":\"https://item.taobao.com/item.htm?id=9\",\"itemId\":\"9\"}]";
        SkuSearchProperties props = new SkuSearchProperties();
        props.setClient("apify");
        props.getApify().setToken("t");
        ApifyTaobaoSkuSearchClient client = new ApifyTaobaoSkuSearchClient(props, transport);
        List<SkuSearchHit> hits = client.search("香薰", "taobao_tbk", 10);
        assertEquals(1, hits.size());
        assertEquals("9", hits.get(0).getRawRef());
        assertEquals("taobao_apify", hits.get(0).getPlatform());
    }

    @Test
    void search_propagates_transport_failure() {
        ApifyActorTransport transport = (a, t, ms, b) -> {
            throw new IllegalStateException("http_error");
        };
        SkuSearchProperties props = new SkuSearchProperties();
        props.getApify().setToken("t");
        ApifyTaobaoSkuSearchClient client = new ApifyTaobaoSkuSearchClient(props, transport);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.search("香薰", "taobao_tbk", 5));
        assertTrue(ex.getMessage().contains("http_error"));
    }

    @Test
    void search_blank_query_skips_transport() {
        AtomicInteger calls = new AtomicInteger();
        ApifyActorTransport transport = (a, t, ms, b) -> {
            calls.incrementAndGet();
            return "[]";
        };
        SkuSearchProperties props = new SkuSearchProperties();
        props.getApify().setToken("t");
        ApifyTaobaoSkuSearchClient client = new ApifyTaobaoSkuSearchClient(props, transport);
        assertTrue(client.search("", "taobao_tbk", 5).isEmpty());
        assertTrue(client.search("   ", "taobao_tbk", 5).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void search_clamps_page_size_to_twenty() {
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> {
            assertTrue(body.contains("\"maxItems\":20"));
            return "[]";
        };
        SkuSearchProperties props = new SkuSearchProperties();
        props.getApify().setToken("t");
        ApifyTaobaoSkuSearchClient client = new ApifyTaobaoSkuSearchClient(props, transport);
        client.search("香薰", "taobao_tbk", 100);
    }
}

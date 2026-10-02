package com.xmut.ebus.extension.tool.xhs;

import com.xmut.ebus.extension.tool.sku.ApifyActorTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyXhsNoteSearchClientTest {

    @Test
    void search_maps_transport_payload_and_binds_actor() {
        AtomicReference<String> actor = new AtomicReference<String>();
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> {
            actor.set(actorId);
            assertTrue(body.contains("\"keyword\":\"厨房收纳\""));
            return "[{\"title\":\"a\",\"url\":\"https://www.xiaohongshu.com/explore/9\",\"noteId\":\"9\"}]";
        };
        XhsNoteSearchProperties props = new XhsNoteSearchProperties();
        props.setClient("apify");
        props.getApify().setToken("t");
        ApifyXhsNoteSearchClient client = new ApifyXhsNoteSearchClient(props, transport);
        List<XhsNoteSearchHit> hits = client.search("厨房收纳", 10);
        assertEquals(1, hits.size());
        assertEquals("9", hits.get(0).getNoteId());
        assertEquals("opspilot.cc/xiaohongshu-keyword-search-scraper", actor.get());
    }

    @Test
    void search_blank_query_skips_transport() {
        AtomicInteger calls = new AtomicInteger();
        ApifyActorTransport transport = (a, t, ms, b) -> {
            calls.incrementAndGet();
            return "[]";
        };
        XhsNoteSearchProperties props = new XhsNoteSearchProperties();
        props.getApify().setToken("t");
        ApifyXhsNoteSearchClient client = new ApifyXhsNoteSearchClient(props, transport);
        assertTrue(client.search("", 5).isEmpty());
        assertTrue(client.search("   ", 5).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void search_missing_token_throws() {
        ApifyActorTransport transport = (a, t, ms, b) -> "[]";
        XhsNoteSearchProperties props = new XhsNoteSearchProperties();
        ApifyXhsNoteSearchClient client = new ApifyXhsNoteSearchClient(props, transport);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.search("杯垫", 5));
        assertTrue(ex.getMessage().contains("missing_token"));
    }
}

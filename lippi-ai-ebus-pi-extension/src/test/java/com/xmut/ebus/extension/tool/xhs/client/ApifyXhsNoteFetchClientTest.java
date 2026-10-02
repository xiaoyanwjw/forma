package com.xmut.ebus.extension.tool.xhs.client;

import com.xmut.ebus.extension.common.ApifyActorTransport;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteFetchHit;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteFetchProperties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyXhsNoteFetchClientTest {

    @Test
    void fetch_posts_noteUrls_and_binds_actor() {
        AtomicReference<String> actor = new AtomicReference<String>();
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> {
            actor.set(actorId);
            assertTrue(body.contains("\"noteUrls\""));
            assertTrue(body.contains("https://www.xiaohongshu.com/explore/n1"));
            return "[{\"title\":\"a\",\"content\":\"正文一段\",\"url\":\"https://www.xiaohongshu.com/explore/n1\"}]";
        };
        XhsNoteFetchProperties props = new XhsNoteFetchProperties();
        props.setClient("apify");
        props.getApify().setToken("t");
        ApifyXhsNoteFetchClient client = new ApifyXhsNoteFetchClient(props, transport);
        XhsNoteFetchHit hit = client.fetch("https://www.xiaohongshu.com/explore/n1");
        assertEquals("a", hit.getTitle());
        assertEquals("正文一段", hit.getBody());
        assertEquals("https://www.xiaohongshu.com/explore/n1", hit.getNoteUrl());
        assertEquals("khadinakbar/xiaohongshu-note-detail-scraper", actor.get());
    }

    @Test
    void fetch_blank_ref_skips_transport() {
        AtomicInteger calls = new AtomicInteger();
        ApifyActorTransport transport = (a, t, ms, b) -> {
            calls.incrementAndGet();
            return "[]";
        };
        XhsNoteFetchProperties props = new XhsNoteFetchProperties();
        props.getApify().setToken("t");
        ApifyXhsNoteFetchClient client = new ApifyXhsNoteFetchClient(props, transport);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.fetch("  "));
        assertTrue(ex.getMessage().contains("note_ref_required"));
        assertEquals(0, calls.get());
    }

    @Test
    void fetch_missing_token_throws() {
        ApifyActorTransport transport = (a, t, ms, b) -> "[]";
        XhsNoteFetchProperties props = new XhsNoteFetchProperties();
        ApifyXhsNoteFetchClient client = new ApifyXhsNoteFetchClient(props, transport);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.fetch("https://www.xiaohongshu.com/explore/n1"));
        assertTrue(ex.getMessage().contains("missing_token"));
    }
}

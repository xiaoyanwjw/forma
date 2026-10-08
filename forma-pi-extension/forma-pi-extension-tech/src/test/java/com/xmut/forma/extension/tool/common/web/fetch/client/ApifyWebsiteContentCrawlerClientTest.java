package com.xmut.forma.extension.tool.common.web.fetch.client;

import com.xmut.forma.extension.common.ApifyActorTransport;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchProperties;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyWebsiteContentCrawlerClientTest {

    @Test
    void fetch_posts_single_page_wcc_input() throws Exception {
        AtomicReference<String> actor = new AtomicReference<String>();
        AtomicReference<String> sent = new AtomicReference<String>();
        AtomicReference<Long> timeout = new AtomicReference<Long>();
        String fixture = fixtureJson();
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> {
            actor.set(actorId);
            sent.set(body);
            timeout.set(Long.valueOf(timeoutMs));
            assertEquals("tok", token);
            return fixture;
        };
        WebFetchProperties props = new WebFetchProperties();
        props.getApify().setToken("tok");
        ApifyWebsiteContentCrawlerClient client = new ApifyWebsiteContentCrawlerClient(props, transport);
        WebFetchHit hit = client.fetch("https://example.com/product");
        assertTrue(actor.get().contains("website-content-crawler"));
        assertEquals("apify/website-content-crawler", actor.get());
        assertTrue(sent.get().contains("\"maxCrawlDepth\":0"));
        assertTrue(sent.get().contains("\"maxCrawlPages\":1"));
        assertTrue(sent.get().contains("\"maxResults\":1"));
        assertTrue(sent.get().contains("\"crawlerType\":\"playwright:firefox\""));
        assertTrue(sent.get().contains("\"useApifyProxy\":true"));
        assertTrue(sent.get().contains("\"saveMarkdown\":true"));
        assertTrue(sent.get().contains("\"useSitemaps\":false"));
        assertTrue(sent.get().contains("https://example.com/product"));
        assertEquals(120000L, timeout.get().longValue());
        assertEquals("示例产品", hit.getTitle());
        assertEquals("https://example.com/product", hit.getFinalUrl());
    }

    @Test
    void fetch_missing_token_skips_transport() {
        AtomicInteger calls = new AtomicInteger();
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> {
            calls.incrementAndGet();
            return "[]";
        };
        WebFetchProperties props = new WebFetchProperties();
        ApifyWebsiteContentCrawlerClient client = new ApifyWebsiteContentCrawlerClient(props, transport);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.fetch("https://example.com/product"));
        assertEquals("missing_token", ex.getMessage());
        assertEquals(0, calls.get());
    }

    @Test
    void fetch_short_body_is_empty_body() {
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) ->
                "[{\"url\":\"https://example.com/a\",\"text\":\"hi\",\"metadata\":{\"title\":\"t\"}}]";
        WebFetchProperties props = new WebFetchProperties();
        props.getApify().setToken("tok");
        ApifyWebsiteContentCrawlerClient client = new ApifyWebsiteContentCrawlerClient(props, transport);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> client.fetch("https://example.com/a"));
        assertEquals("empty_body", ex.getMessage());
    }

    private static String fixtureJson() throws Exception {
        InputStream in = ApifyWebsiteContentCrawlerClientTest.class
                .getResourceAsStream("/techdigest/fixtures/wcc-dataset.json");
        if (in == null) {
            throw new IllegalStateException("missing fixture");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}

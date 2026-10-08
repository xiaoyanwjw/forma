package com.xmut.forma.extension.tool.product.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.excerpt.ChunkExcerpt;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestChunk;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestCompetitorToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAGE = "https://example.com/product";
    private static final String BODY =
            "Acme is a product for teams. Pricing starts at $29. "
                    + "It helps shipping faster with automation and audits.";

    @TempDir
    Path workspace;

    @Test
    void fetch_success_returns_excerpts_without_calling_crawl() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        AtomicInteger excerptCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                assertEquals(PAGE, url);
                return new WebFetchHit(PAGE, "Acme", BODY, false);
            }
        };
        ChunkExcerptPort excerpt = recordingExcerpt(excerptCalls, "Acme is a product for teams.");
        IngestCompetitorToolHandler handler = new IngestCompetitorToolHandler(
                new PageIngestService(fetch, excerpt));

        ToolResult result = handler.handle(callUrl(PAGE), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertTrue(root.get("ok").asBoolean());
        assertEquals("fetch", root.get("source").asText());
        assertEquals(PAGE, root.get("sourceUrl").asText());
        assertEquals("source.md", root.get("sourcePath").asText());
        assertTrue(root.get("excerpts").isArray());
        assertTrue(root.get("excerpts").size() > 0);
        assertEquals(1, fetchCalls.get());
        assertEquals(1, excerptCalls.get());
        assertTrue(Files.isRegularFile(workspace.resolve("source.md")));
        assertEquals(BODY, new String(Files.readAllBytes(workspace.resolve("source.md")), StandardCharsets.UTF_8));
    }

    @Test
    void no_url_no_paste_fails() {
        AtomicInteger fetchCalls = new AtomicInteger();
        AtomicInteger excerptCalls = new AtomicInteger();
        PageFetchPort fetch = countingFetch(fetchCalls);
        ChunkExcerptPort excerpt = recordingExcerpt(excerptCalls, "x");
        IngestCompetitorToolHandler handler = new IngestCompetitorToolHandler(
                new PageIngestService(fetch, excerpt));

        ToolResult result = handler.handle(callEmpty(), ctx());

        assertFalse(result.isSuccess());
        assertEquals(0, fetchCalls.get());
        assertEquals(0, excerptCalls.get());
    }

    @Test
    void fetch_failure_does_not_excerpt() {
        AtomicInteger fetchCalls = new AtomicInteger();
        AtomicInteger excerptCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                throw new IllegalStateException("timeout: read timed out");
            }
        };
        ChunkExcerptPort excerpt = recordingExcerpt(excerptCalls, "x");
        IngestCompetitorToolHandler handler = new IngestCompetitorToolHandler(
                new PageIngestService(fetch, excerpt));

        ToolResult result = handler.handle(callUrl(PAGE), ctx());

        assertFalse(result.isSuccess());
        assertEquals(1, fetchCalls.get());
        assertEquals(0, excerptCalls.get());
    }

    @Test
    void paste_skips_fetch_and_returns_excerpts() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        AtomicInteger excerptCalls = new AtomicInteger();
        PageFetchPort fetch = countingFetch(fetchCalls);
        ChunkExcerptPort excerpt = recordingExcerpt(excerptCalls, "Acme is a product for teams.");
        IngestCompetitorToolHandler handler = new IngestCompetitorToolHandler(
                new PageIngestService(fetch, excerpt));

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("paste", BODY);
        args.put("url", PAGE);
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", IngestCompetitorToolHandler.TOOL_NAME, args), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals("paste", root.get("source").asText());
        assertEquals(PAGE, root.get("sourceUrl").asText());
        assertTrue(root.get("excerpts").size() > 0);
        assertEquals(0, fetchCalls.get());
        assertEquals(1, excerptCalls.get());
    }

    private ToolContext ctx() {
        return new ToolContext("r1", "t1", null, workspace.toString());
    }

    private static ToolCallEntry callUrl(String url) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("url", url);
        return new ToolCallEntry("c1", IngestCompetitorToolHandler.TOOL_NAME, args);
    }

    private static ToolCallEntry callEmpty() {
        return new ToolCallEntry("c1", IngestCompetitorToolHandler.TOOL_NAME,
                JsonNodeFactory.instance.objectNode());
    }

    private static PageFetchPort countingFetch(final AtomicInteger calls) {
        return new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                calls.incrementAndGet();
                throw new AssertionError("fetch must not be called");
            }
        };
    }

    private static ChunkExcerptPort recordingExcerpt(final AtomicInteger calls, final String quote) {
        return new ChunkExcerptPort() {
            @Override
            public List<ChunkExcerpt> excerpt(List<TechDigestChunk> chunks) {
                calls.incrementAndGet();
                List<ChunkExcerpt> out = new ArrayList<ChunkExcerpt>();
                if (chunks == null) {
                    return out;
                }
                for (int i = 0; i < chunks.size(); i++) {
                    TechDigestChunk chunk = chunks.get(i);
                    String heading = chunk == null ? "" : chunk.getHeading();
                    out.add(new ChunkExcerpt(heading, Collections.singletonList(quote)));
                }
                return out;
            }
        };
    }
}

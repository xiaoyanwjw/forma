package com.xmut.forma.extension.tool.product.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchProductsToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String URL_A = "https://example.com/a";
    private static final String URL_B = "https://example.com/b";

    @TempDir
    Path workspace;

    @Test
    void deepFetch_zero_skips_page_fetch() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        PageFetchPort fetch = countingFetch(fetchCalls);
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(fetch);

        ToolResult result = handler.handle(call(candidatesTwo(), 0), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals(0, root.get("deepFetch").asInt());
        assertEquals(2, root.get("candidates").size());
        assertEquals(0, fetchCalls.get());
        assertFalse(root.get("candidates").get(0).has("evidence")
                && root.get("candidates").get(0).get("evidence").asText().length() > 0);
    }

    @Test
    void deepFetch_one_fetches_only_first_candidate_with_url() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                assertEquals(URL_A, url);
                return new WebFetchHit(URL_A, "Alpha",
                        "Alpha ships faster for teams building agents.", false);
            }
        };
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(fetch);

        ArrayNode candidates = JsonNodeFactory.instance.arrayNode();
        candidates.add(candidate("NoUrl", "", ""));
        candidates.add(candidate("Alpha", "tag", URL_A));
        candidates.add(candidate("Beta", "tag", URL_B));

        ToolResult result = handler.handle(call(candidates, 1), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals(1, root.get("deepFetch").asInt());
        assertEquals(1, fetchCalls.get());
        JsonNode researched = root.get("candidates").get(1);
        assertTrue(researched.has("evidence"));
        assertTrue(researched.get("evidence").asText().length() > 0);
        assertFalse(root.get("candidates").get(2).has("evidence")
                && root.get("candidates").get(2).get("evidence").asText().length() > 0);
    }

    @Test
    void deepFetch_defaults_to_one_and_clamps_above_one() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                return new WebFetchHit(url, "T", "Body evidence line for product.", false);
            }
        };
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(fetch);

        ToolResult omitted = handler.handle(call(candidatesTwo(), null), ctx());
        assertTrue(omitted.isSuccess());
        assertEquals(1, MAPPER.readTree(omitted.getOutput()).get("deepFetch").asInt());
        assertEquals(1, fetchCalls.get());

        fetchCalls.set(0);
        ToolResult clamped = handler.handle(call(candidatesTwo(), 9), ctx());
        assertTrue(clamped.isSuccess());
        assertEquals(1, MAPPER.readTree(clamped.getOutput()).get("deepFetch").asInt());
        assertEquals(1, fetchCalls.get());
    }

    @Test
    void fetch_failure_keeps_candidate_with_empty_evidence() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                throw new IllegalStateException("timeout: read timed out");
            }
        };
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(fetch);

        ToolResult result = handler.handle(call(candidatesTwo(), 1), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals(2, root.get("candidates").size());
        JsonNode first = root.get("candidates").get(0);
        assertEquals("Alpha", first.get("title").asText());
        assertEquals(URL_A, first.get("url").asText());
        String evidence = first.has("evidence") ? first.get("evidence").asText("") : "";
        assertEquals("", evidence);
        assertEquals(1, fetchCalls.get());
    }

    @Test
    void missing_candidates_fails_without_fetch() {
        AtomicInteger fetchCalls = new AtomicInteger();
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(countingFetch(fetchCalls));

        ToolResult result = handler.handle(
                new ToolCallEntry("c1", ResearchProductsToolHandler.TOOL_NAME,
                        JsonNodeFactory.instance.objectNode()),
                ctx());

        assertFalse(result.isSuccess());
        assertEquals(0, fetchCalls.get());
    }

    @Test
    void candidates_path_loads_from_workspace() throws Exception {
        ObjectNode fileRoot = JsonNodeFactory.instance.objectNode();
        fileRoot.set("candidates", candidatesTwo());
        Path path = workspace.resolve("candidates.json");
        Files.write(path, MAPPER.writeValueAsBytes(fileRoot));

        AtomicInteger fetchCalls = new AtomicInteger();
        PageFetchPort fetch = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                fetchCalls.incrementAndGet();
                return new WebFetchHit(url, "Alpha", "Evidence from path load.", false);
            }
        };
        ResearchProductsToolHandler handler = new ResearchProductsToolHandler(fetch);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("candidatesPath", "candidates.json");
        args.put("deepFetch", 1);
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", ResearchProductsToolHandler.TOOL_NAME, args), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals(2, root.get("candidates").size());
        assertTrue(root.get("candidates").get(0).get("evidence").asText().contains("Evidence from path load"));
        assertEquals(1, fetchCalls.get());
        assertTrue(Files.isRegularFile(path));
        assertTrue(new String(Files.readAllBytes(path), StandardCharsets.UTF_8).contains("Alpha"));
    }

    private ToolContext ctx() {
        return new ToolContext("r1", "t1", null, workspace.toString());
    }

    private static ToolCallEntry call(ArrayNode candidates, Integer deepFetch) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.set("candidates", candidates);
        if (deepFetch != null) {
            args.put("deepFetch", deepFetch.intValue());
        }
        return new ToolCallEntry("c1", ResearchProductsToolHandler.TOOL_NAME, args);
    }

    private static ArrayNode candidatesTwo() {
        ArrayNode candidates = JsonNodeFactory.instance.arrayNode();
        candidates.add(candidate("Alpha", "alpha tag", URL_A));
        candidates.add(candidate("Beta", "beta tag", URL_B));
        return candidates;
    }

    private static ObjectNode candidate(String title, String tagline, String url) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("title", title);
        node.put("tagline", tagline);
        node.put("url", url);
        node.put("source", "ph");
        return node;
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
}

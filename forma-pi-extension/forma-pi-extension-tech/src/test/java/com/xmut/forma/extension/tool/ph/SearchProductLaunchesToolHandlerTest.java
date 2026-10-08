package com.xmut.forma.extension.tool.ph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.common.ApifyActorTransport;
import com.xmut.forma.extension.tool.ph.client.ApifyProductHuntSearchClient;
import com.xmut.forma.extension.tool.ph.port.ProductLaunchSearchProperties;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchProductLaunchesToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void topic_ai_coding_maps_mock_actor_items_to_ph_candidates() throws Exception {
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> fixtureFivePlusAiCoding();
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "test-token");

        ToolResult result = handler.handle(call("AI coding", null), new ToolContext("r1", "t1"));

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        JsonNode candidates = root.get("candidates");
        assertTrue(candidates.isArray());
        assertTrue(candidates.size() > 0);
        assertTrue(candidates.size() <= SearchProductLaunchesToolHandler.DEFAULT_MAX_CANDIDATES);
        for (JsonNode row : candidates) {
            assertTrue(row.hasNonNull("title") && row.get("title").asText().trim().length() > 0);
            assertTrue(row.has("url"));
            assertEquals("ph", row.get("source").asText());
        }
    }

    @Test
    void filters_by_topic_dedupes_and_truncates() throws Exception {
        ApifyActorTransport transport = (actorId, token, timeoutMs, body) -> "["
                + item("Cursor AI Coding", "AI coding assistant", "https://www.producthunt.com/posts/a", 10)
                + ","
                + item("Cursor AI Coding", "dup title", "https://www.producthunt.com/posts/b", 9)
                + ","
                + item("Other Tool", "unrelated tagline", "https://www.producthunt.com/posts/c", 8)
                + ","
                + item("CodePilot", "best AI coding agent", "https://www.producthunt.com/posts/a", 7)
                + ","
                + item("ShipFast", "AI coding for founders", "https://www.producthunt.com/posts/d", 6)
                + ","
                + item("LaunchPad", "AI coding launch kit", "https://www.producthunt.com/posts/e", 5)
                + ","
                + item("DevSpark", "AI coding sparks", "https://www.producthunt.com/posts/f", 4)
                + "]";
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "test-token");

        ToolResult result = handler.handle(call("AI coding", 3), new ToolContext("r1", "t1"));

        assertTrue(result.isSuccess());
        JsonNode candidates = MAPPER.readTree(result.getOutput()).get("candidates");
        assertEquals(3, candidates.size());
        assertEquals("Cursor AI Coding", candidates.get(0).get("title").asText());
        assertEquals("https://www.producthunt.com/posts/a", candidates.get(0).get("url").asText());
        assertEquals("ShipFast", candidates.get(1).get("title").asText());
        assertEquals("LaunchPad", candidates.get(2).get("title").asText());
    }

    @Test
    void missing_token_returns_structured_error_without_throw() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ApifyActorTransport transport = (a, t, ms, b) -> {
            calls.incrementAndGet();
            return "[]";
        };
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "");

        ToolResult result = handler.handle(call("AI coding", null), new ToolContext("r1", "t1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null && result.getErrorMessage().contains("missing_token"));
        assertEquals(0, calls.get());
    }

    @Test
    void empty_actor_dataset_returns_structured_error_without_throw() {
        ApifyActorTransport transport = (a, t, ms, b) -> "[]";
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "test-token");

        ToolResult result = handler.handle(call("AI coding", null), new ToolContext("r1", "t1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null
                && result.getErrorMessage().toLowerCase().contains("empty"));
    }

    @Test
    void transport_failure_returns_structured_error_without_throw() {
        ApifyActorTransport transport = (a, t, ms, b) -> {
            throw new IllegalStateException("apify_error: boom");
        };
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "test-token");

        ToolResult result = handler.handle(call("AI coding", null), new ToolContext("r1", "t1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null && result.getErrorMessage().contains("search_product_launches"));
    }

    @Test
    void missing_topic_fails() {
        ApifyActorTransport transport = (a, t, ms, b) -> {
            throw new AssertionError("must not call transport");
        };
        SearchProductLaunchesToolHandler handler = handlerWith(transport, "test-token");

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ToolResult result = handler.handle(
                new ToolCallEntry("c2", SearchProductLaunchesToolHandler.TOOL_NAME, args),
                new ToolContext("r1", "t1"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage() != null && result.getErrorMessage().contains("topic"));
    }

    private static SearchProductLaunchesToolHandler handlerWith(ApifyActorTransport transport, String token) {
        ProductLaunchSearchProperties props = new ProductLaunchSearchProperties();
        props.getApify().setToken(token);
        props.getApify().setActorId(ProductLaunchSearchProperties.DEFAULT_ACTOR_ID);
        ApifyProductHuntSearchClient client = new ApifyProductHuntSearchClient(props, transport);
        return new SearchProductLaunchesToolHandler(client, props);
    }

    private static ToolCallEntry call(String topic, Integer maxCandidates) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("topic", topic);
        if (maxCandidates != null) {
            args.put("maxCandidates", maxCandidates);
        }
        return new ToolCallEntry("c1", SearchProductLaunchesToolHandler.TOOL_NAME, args);
    }

    private static String fixtureFivePlusAiCoding() {
        return "["
                + item("Cursor Rules Hub", "Share AI coding rules", "https://www.producthunt.com/posts/cursor-rules", 120)
                + ","
                + item("Devin Clone", "AI coding agent for teams", "https://www.producthunt.com/posts/devin-clone", 90)
                + ","
                + item("PromptKit", "AI coding prompts library", "https://www.producthunt.com/posts/promptkit", 80)
                + ","
                + item("CodePilot", "Ship with AI coding copilots", "https://www.producthunt.com/posts/codepilot", 70)
                + ","
                + item("StackAI", "AI coding for stack overflow", "https://www.producthunt.com/posts/stackai", 60)
                + ","
                + item("Unrelated CRM", "sales CRM only", "https://www.producthunt.com/posts/crm", 50)
                + "]";
    }

    private static String item(String name, String tagline, String url, int votes) {
        return "{\"name\":\"" + name + "\",\"tagline\":\"" + tagline
                + "\",\"productUrl\":\"" + url + "\",\"votesCount\":" + votes
                + ",\"launchDate\":\"2026-10-08T00:00:00Z\"}";
    }
}

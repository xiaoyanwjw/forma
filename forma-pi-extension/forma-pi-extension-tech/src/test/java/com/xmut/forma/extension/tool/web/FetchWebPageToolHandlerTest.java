package com.xmut.forma.extension.tool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.web.client.MockWebFetchClient;
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

class FetchWebPageToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAGE = "https://example.com/product";

    @TempDir
    Path workspace;

    @Test
    void https_success_writes_source_md_without_leaking_body() throws Exception {
        MockWebFetchClient mock = new MockWebFetchClient();
        WebFetchHit preview = mock.fetch(PAGE);
        String marker = uniqueSlice(preview.getText(), 20);
        assertEquals(20, marker.length());
        assertTrue(preview.getText().length() >= 500);
        assertEquals("Mock 科技页", preview.getTitle());

        FetchWebPageToolHandler handler = new FetchWebPageToolHandler(mock);
        ToolResult result = handler.handle(call(PAGE), ctx());

        assertTrue(result.isSuccess());
        String output = result.getOutput();
        assertFalse(output.contains(marker));
        JsonNode root = MAPPER.readTree(output);
        assertTrue(root.get("ok").asBoolean());
        assertEquals(PAGE, root.get("finalUrl").asText());
        assertEquals("Mock 科技页", root.get("title").asText());
        assertEquals(preview.getText().length(), root.get("charCount").asInt());
        assertEquals("source.md", root.get("sourcePath").asText());
        assertEquals("apify", root.get("extractMethod").asText());
        assertFalse(root.get("truncated").asBoolean());
        assertFalse(root.has("text"));
        assertTrue(root.get("errorCode").isNull() || root.get("errorCode").asText().isEmpty());

        String written = new String(Files.readAllBytes(workspace.resolve("source.md")), StandardCharsets.UTF_8);
        assertEquals(preview.getText(), written);
        assertTrue(written.contains(marker));
    }

    @Test
    void bad_url_does_not_fetch_or_write() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PageFetchPort port = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                calls.incrementAndGet();
                throw new AssertionError("port must not be called");
            }
        };
        FetchWebPageToolHandler handler = new FetchWebPageToolHandler(port);
        ToolResult result = handler.handle(call("http://127.0.0.1/secret"), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertFalse(root.get("ok").asBoolean());
        assertEquals("bad_url", root.get("errorCode").asText());
        assertTrue(root.get("message").asText().length() > 0);
        assertFalse(root.has("text"));
        assertFalse(result.getOutput().contains("127.0.0.1"));
        assertEquals(0, calls.get());
        assertFalse(Files.exists(workspace.resolve("source.md")));
    }

    @Test
    void port_errors_map_to_error_codes_without_body() throws Exception {
        assertErrorCode(new IllegalStateException("missing_token"), "missing_token");
        assertErrorCode(new IllegalStateException("empty_body"), "empty_body");
        assertErrorCode(new IllegalStateException("timeout: read timed out"), "timeout");
        assertErrorCode(new IllegalStateException("apify_error: empty dataset"), "apify_error");
    }

    private void assertErrorCode(RuntimeException failure, String errorCode) throws Exception {
        PageFetchPort port = new PageFetchPort() {
            @Override
            public WebFetchHit fetch(String url) {
                throw failure;
            }
        };
        ToolResult result = new FetchWebPageToolHandler(port).handle(call(PAGE), ctx());
        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertFalse(root.get("ok").asBoolean());
        assertEquals(errorCode, root.get("errorCode").asText());
        assertTrue(root.get("message").asText().length() > 0);
        assertFalse(root.has("text"));
        assertFalse(Files.exists(workspace.resolve("source.md")));
    }

    private ToolContext ctx() {
        return new ToolContext("r1", "t1", null, workspace.toString());
    }

    private static ToolCallEntry call(String url) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("url", url);
        return new ToolCallEntry("c1", FetchWebPageToolHandler.TOOL_NAME, args);
    }

    private static String uniqueSlice(String text, int len) {
        for (int i = 0; i + len <= text.length(); i++) {
            String slice = text.substring(i, i + len);
            if (text.indexOf(slice) == i && text.indexOf(slice, i + 1) < 0) {
                return slice;
            }
        }
        throw new AssertionError("mock body has no unique " + len + "-char slice");
    }
}

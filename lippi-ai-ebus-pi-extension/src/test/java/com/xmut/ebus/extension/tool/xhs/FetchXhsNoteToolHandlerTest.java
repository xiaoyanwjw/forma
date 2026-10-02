package com.xmut.ebus.extension.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.extension.config.SkuToolsConfiguration;
import com.xmut.ebus.extension.config.XhsToolsConfiguration;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteFetchHit;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteFetchPort;
import com.xmut.lims.pi.agent.config.PiAutoConfiguration;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FetchXhsNoteToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NOTE_URL = "https://www.xiaohongshu.com/explore/abc123";

    @Mock
    private XhsNoteFetchPort port;

    private FetchXhsNoteToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FetchXhsNoteToolHandler(port);
    }

    @Test
    void handle_ok_writes_detail_json() throws Exception {
        when(port.fetch(NOTE_URL)).thenReturn(new XhsNoteFetchHit(
                "厨房收纳",
                "租房党抽屉整理正文",
                NOTE_URL,
                "作者A",
                Arrays.asList("收纳", "租房")));

        ToolResult r = handler.handle(callWithUrl("url", NOTE_URL), new ToolContext("r1", "t1"));

        assertTrue(r.isSuccess());
        assertEquals(FetchXhsNoteToolHandler.TOOL_NAME, r.getToolName());
        JsonNode root = MAPPER.readTree(r.getOutput());
        assertEquals("厨房收纳", root.get("title").asText());
        assertEquals("租房党抽屉整理正文", root.get("body").asText());
        assertEquals(NOTE_URL, root.get("noteUrl").asText());
        assertEquals("作者A", root.get("author").asText());
        assertTrue(root.get("tags").isArray());
        assertEquals(2, root.get("tags").size());
        assertEquals("收纳", root.get("tags").get(0).asText());
        verify(port).fetch(NOTE_URL);
    }

    @Test
    void handle_noteUrl_alias() throws Exception {
        when(port.fetch(NOTE_URL)).thenReturn(new XhsNoteFetchHit(
                "杯垫",
                "硅胶杯垫分享",
                NOTE_URL,
                null,
                null));

        ToolResult r = handler.handle(callWithUrl("noteUrl", NOTE_URL), new ToolContext("r1", "t1"));

        assertTrue(r.isSuccess());
        JsonNode root = MAPPER.readTree(r.getOutput());
        assertEquals("杯垫", root.get("title").asText());
        assertEquals("硅胶杯垫分享", root.get("body").asText());
        verify(port).fetch(NOTE_URL);
    }

    @Test
    void handle_missing_url_fails() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ToolResult r = handler.handle(
                new ToolCallEntry("c2", FetchXhsNoteToolHandler.TOOL_NAME, args),
                new ToolContext("r1", "t1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage() != null && r.getErrorMessage().contains("url required"));
        verify(port, never()).fetch(anyString());
    }

    @Test
    void handle_empty_body_fails() {
        when(port.fetch(NOTE_URL)).thenReturn(new XhsNoteFetchHit("t", "  ", NOTE_URL, null, null));

        ToolResult r = handler.handle(callWithUrl("url", NOTE_URL), new ToolContext("r1", "t1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage() != null && r.getErrorMessage().contains("fetch_xhs_note empty body"));
    }

    @Test
    void handle_port_exception_fails() {
        when(port.fetch(NOTE_URL)).thenThrow(new IllegalStateException("apify_error"));

        ToolResult r = handler.handle(callWithUrl("url", NOTE_URL), new ToolContext("r1", "t1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage() != null && r.getErrorMessage().contains("fetch_xhs_note failed"));
    }

    @Test
    void catalogResolvesFetchXhsNote() {
        new ApplicationContextRunner()
                .withUserConfiguration(SkuToolsConfiguration.class, XhsToolsConfiguration.class)
                .withConfiguration(AutoConfigurations.of(PiAutoConfiguration.class))
                .run(context -> {
                    assertTrue(context.getBean(ToolCatalog.class).resolve("fetch_xhs_note").isPresent());
                    assertTrue(context.getBean(ToolCatalog.class).resolve("search_xhs_note").isPresent());
                });
    }

    private static ToolCallEntry callWithUrl(String field, String value) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put(field, value);
        return new ToolCallEntry("c1", FetchXhsNoteToolHandler.TOOL_NAME, args);
    }
}

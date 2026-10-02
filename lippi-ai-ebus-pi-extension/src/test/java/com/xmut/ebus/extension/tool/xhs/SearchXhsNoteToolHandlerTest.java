package com.xmut.ebus.extension.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchHit;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchPort;
import com.xmut.ebus.extension.tool.xhs.search.XhsNoteSearcher;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchXhsNoteToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private XhsNoteSearcher searcher;

    @Mock
    private XhsNoteSearchPort port;

    private SearchXhsNoteToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SearchXhsNoteToolHandler(searcher);
    }

    @Test
    void handle_ok_writes_hits_json() throws Exception {
        XhsNoteSearchHit hit = new XhsNoteSearchHit(
                "n1",
                "厨房收纳",
                "租房党抽屉整理",
                "https://www.xiaohongshu.com/explore/n1",
                "128",
                "作者A");
        when(searcher.search(eq("厨房收纳"), eq(10))).thenReturn(Collections.singletonList(hit));

        ToolResult r = handler.handle(callWithQuery("厨房收纳"), new ToolContext("r1", "t1"));

        assertTrue(r.isSuccess());
        assertEquals(SearchXhsNoteToolHandler.TOOL_NAME, r.getToolName());
        JsonNode root = MAPPER.readTree(r.getOutput());
        JsonNode hits = root.get("hits");
        assertTrue(hits.isArray());
        assertEquals(1, hits.size());
        JsonNode row = hits.get(0);
        assertEquals("n1", row.get("noteId").asText());
        assertEquals("厨房收纳", row.get("title").asText());
        assertEquals("租房党抽屉整理", row.get("desc").asText());
        assertEquals("https://www.xiaohongshu.com/explore/n1", row.get("noteUrl").asText());
        assertEquals("128", row.get("likedCount").asText());
        assertEquals("作者A", row.get("author").asText());
        verify(searcher).search(eq("厨房收纳"), eq(10));
        verify(port, never()).search(anyString(), anyInt());
    }

    @Test
    void handle_empty_fails() {
        when(searcher.search(eq("无结果"), eq(10))).thenReturn(Collections.emptyList());

        ToolResult r = handler.handle(callWithQuery("无结果"), new ToolContext("r1", "t1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage() != null && r.getErrorMessage().contains("search_xhs_note empty hits"));
        verify(searcher).search(eq("无结果"), eq(10));
        verify(port, never()).search(anyString(), anyInt());
    }

    @Test
    void handle_missing_query_fails() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ToolResult r = handler.handle(
                new ToolCallEntry("c2", SearchXhsNoteToolHandler.TOOL_NAME, args),
                new ToolContext("r1", "t1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getErrorMessage() != null && r.getErrorMessage().contains("query required"));
        verify(searcher, never()).search(anyString(), anyInt());
    }

    private static ToolCallEntry callWithQuery(String query) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", query);
        return new ToolCallEntry("c1", SearchXhsNoteToolHandler.TOOL_NAME, args);
    }
}

package com.xmut.ebus.application.business.agent.tool.sku;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchSkuToolHandlerTest {

    @Mock
    private SkuSearcher searcher;

    @Mock
    private SkuSearchPort port;

    private SearchSkuToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SearchSkuToolHandler(searcher);
    }

    @Test
    void handle_callsSearcherNotPort() {
        SkuSearchHit hit = new SkuSearchHit(
                "taobao_tbk", "香薰蜡烛", "1", "cat", "https://example.com/1", "ref");
        when(searcher.search(eq("香薰"), eq(10))).thenReturn(Collections.singletonList(hit));

        ToolResult r = handler.handle(callWithQuery("香薰"), new ToolContext("r1", "t1"));

        assertTrue(r.isSuccess());
        verify(searcher).search(eq("香薰"), eq(10));
        verify(port, never()).search(anyString(), anyString(), anyInt());
    }

    private static ToolCallEntry callWithQuery(String query) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", query);
        return new ToolCallEntry("c1", SearchSkuToolHandler.TOOL_NAME, args);
    }
}

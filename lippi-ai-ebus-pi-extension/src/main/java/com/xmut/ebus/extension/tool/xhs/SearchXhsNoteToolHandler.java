package com.xmut.ebus.extension.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchHit;
import com.xmut.ebus.extension.tool.xhs.search.XhsNoteSearcher;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Pi tool {@code search_xhs_note}: query Xiaohongshu notes via {@link XhsNoteSearcher}.
 */
public final class SearchXhsNoteToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchXhsNoteToolHandler.class);

    public static final String TOOL_NAME = "search_xhs_note";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 20;

    private final XhsNoteSearcher xhsNoteSearcher;
    private final ObjectMapper objectMapper;

    public SearchXhsNoteToolHandler(XhsNoteSearcher xhsNoteSearcher) {
        this(xhsNoteSearcher, new ObjectMapper());
    }

    SearchXhsNoteToolHandler(XhsNoteSearcher xhsNoteSearcher, ObjectMapper objectMapper) {
        this.xhsNoteSearcher = xhsNoteSearcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String query = extractQuery(call);
            if (!StringUtils.hasText(query)) {
                return ToolResult.failed(callId, TOOL_NAME, "query required");
            }
            List<XhsNoteSearchHit> hits = xhsNoteSearcher.search(query.trim(), resolvePageSize(call));
            if (hits == null || hits.isEmpty()) {
                return ToolResult.failed(callId, TOOL_NAME, "search_xhs_note empty hits");
            }
            return ToolResult.ok(callId, TOOL_NAME, writeHits(hits));
        } catch (Exception ex) {
            log.warn("search_xhs_note failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "search_xhs_note failed: " + ex.getMessage());
        }
    }

    private String writeHits(List<XhsNoteSearchHit> hits) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode array = root.putArray("hits");
        if (hits != null) {
            for (XhsNoteSearchHit hit : hits) {
                if (hit == null) {
                    continue;
                }
                ObjectNode node = array.addObject();
                node.put("noteId", emptyIfNull(hit.getNoteId()));
                node.put("title", emptyIfNull(hit.getTitle()));
                node.put("desc", emptyIfNull(hit.getDesc()));
                node.put("noteUrl", emptyIfNull(hit.getNoteUrl()));
                node.put("likedCount", emptyIfNull(hit.getLikedCount()));
                node.put("author", emptyIfNull(hit.getAuthor()));
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    static String extractQuery(ToolCallEntry call) {
        JsonNode args = arguments(call);
        if (args == null) {
            return null;
        }
        JsonNode query = args.get("query");
        return query == null || query.isNull() ? null : query.asText(null);
    }

    static int resolvePageSize(ToolCallEntry call) {
        JsonNode args = arguments(call);
        if (args == null || !args.has("pageSize") || args.get("pageSize").isNull()) {
            return DEFAULT_PAGE_SIZE;
        }
        int pageSize = args.get("pageSize").asInt(DEFAULT_PAGE_SIZE);
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}

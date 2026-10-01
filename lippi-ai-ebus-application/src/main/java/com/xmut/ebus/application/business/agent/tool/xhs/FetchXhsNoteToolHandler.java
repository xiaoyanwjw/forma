package com.xmut.ebus.application.business.agent.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Pi tool {@code fetch_xhs_note}: load one Xiaohongshu note body via {@link XhsNoteFetchPort}.
 */
public final class FetchXhsNoteToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchXhsNoteToolHandler.class);

    public static final String TOOL_NAME = "fetch_xhs_note";

    private final XhsNoteFetchPort fetchPort;
    private final ObjectMapper objectMapper;

    public FetchXhsNoteToolHandler(XhsNoteFetchPort fetchPort) {
        this(fetchPort, new ObjectMapper());
    }

    FetchXhsNoteToolHandler(XhsNoteFetchPort fetchPort, ObjectMapper objectMapper) {
        this.fetchPort = fetchPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String url = extractUrl(call);
            if (!StringUtils.hasText(url)) {
                return ToolResult.failed(callId, TOOL_NAME, "url required");
            }
            XhsNoteFetchHit hit = fetchPort.fetch(url.trim());
            if (hit == null || !StringUtils.hasText(hit.getBody())) {
                return ToolResult.failed(callId, TOOL_NAME, "fetch_xhs_note empty body");
            }
            return ToolResult.ok(callId, TOOL_NAME, writeHit(hit));
        } catch (Exception ex) {
            log.warn("fetch_xhs_note failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "fetch_xhs_note failed: " + ex.getMessage());
        }
    }

    private String writeHit(XhsNoteFetchHit hit) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", emptyIfNull(hit.getTitle()));
        root.put("body", emptyIfNull(hit.getBody()));
        root.put("noteUrl", emptyIfNull(hit.getNoteUrl()));
        if (StringUtils.hasText(hit.getAuthor())) {
            root.put("author", hit.getAuthor());
        }
        List<String> tags = hit.getTags();
        if (tags != null && !tags.isEmpty()) {
            ArrayNode tagArr = root.putArray("tags");
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    tagArr.add(tag);
                }
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    static String extractUrl(ToolCallEntry call) {
        JsonNode args = arguments(call);
        if (args == null) {
            return null;
        }
        JsonNode url = args.get("url");
        if (url != null && !url.isNull() && StringUtils.hasText(url.asText(null))) {
            return url.asText();
        }
        JsonNode noteUrl = args.get("noteUrl");
        return noteUrl == null || noteUrl.isNull() ? null : noteUrl.asText(null);
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

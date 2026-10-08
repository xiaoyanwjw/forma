package com.xmut.forma.extension.tool.digest.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService.PageIngestOutcome;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService.PageIngestRequest;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pi business tool {@code ingest_digest}: fetch|paste → excerpts for tech digest.
 */
public final class IngestDigestToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(IngestDigestToolHandler.class);

    public static final String TOOL_NAME = "ingest_digest";

    private final PageIngestService ingestService;

    public IngestDigestToolHandler(PageIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            JsonNode args = arguments(call);
            PageIngestOutcome outcome = ingestService.ingest(new PageIngestRequest(
                    LocalFileSupport.workspace(ctx),
                    textOrNull(args, "url"),
                    textOrNull(args, "paste"),
                    textOrNull(args, "sourcePath")));
            if (!outcome.isSuccess()) {
                log.warn("ingest_digest failed code={}", outcome.getErrorCode());
                return ToolResult.failed(callId, TOOL_NAME, outcome.getMessage());
            }
            return ToolResult.ok(callId, TOOL_NAME, outcome.getJson());
        } catch (Exception ex) {
            log.warn("ingest_digest failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "ingest_digest failed");
        }
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }

    private static String textOrNull(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode node = args.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }
}

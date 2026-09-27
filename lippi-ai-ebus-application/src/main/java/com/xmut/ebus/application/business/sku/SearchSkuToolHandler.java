package com.xmut.ebus.application.business.sku;

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
 * Pi tool {@code search_sku}: query SKUs via {@link SkuSearchPort}.
 */
public final class SearchSkuToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchSkuToolHandler.class);

    public static final String TOOL_NAME = "search_sku";
    public static final String DEFAULT_PLATFORM = "taobao_tbk";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 20;

    private final SkuSearchPort skuSearchPort;
    private final ObjectMapper objectMapper;

    public SearchSkuToolHandler(SkuSearchPort skuSearchPort) {
        this(skuSearchPort, new ObjectMapper());
    }

    SearchSkuToolHandler(SkuSearchPort skuSearchPort, ObjectMapper objectMapper) {
        this.skuSearchPort = skuSearchPort;
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
            String platform = extractPlatform(call);
            int pageSize = extractPageSize(call);
            List<SkuSearchHit> hits = skuSearchPort.search(query.trim(), platform, pageSize);
            if (hits == null || hits.isEmpty()) {
                return ToolResult.failed(callId, TOOL_NAME, "search_sku empty hits");
            }
            return ToolResult.ok(callId, TOOL_NAME, writeHits(hits));
        } catch (Exception ex) {
            log.warn("search_sku failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "search_sku failed: " + ex.getMessage());
        }
    }

    private String writeHits(List<SkuSearchHit> hits) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode array = root.putArray("hits");
        if (hits != null) {
            for (SkuSearchHit hit : hits) {
                if (hit == null) {
                    continue;
                }
                ObjectNode node = array.addObject();
                node.put("platform", hit.getPlatform());
                node.put("title", hit.getTitle());
                node.put("price", hit.getPrice());
                node.put("category", hit.getCategory());
                node.put("detailUrl", hit.getDetailUrl());
                node.put("rawRef", hit.getRawRef());
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

    static String extractPlatform(ToolCallEntry call) {
        JsonNode args = arguments(call);
        if (args != null && args.has("platform") && !args.get("platform").isNull()) {
            String platform = args.get("platform").asText(null);
            if (StringUtils.hasText(platform)) {
                return platform.trim();
            }
        }
        return DEFAULT_PLATFORM;
    }

    static int extractPageSize(ToolCallEntry call) {
        JsonNode args = arguments(call);
        int pageSize = DEFAULT_PAGE_SIZE;
        if (args != null && args.has("pageSize") && !args.get("pageSize").isNull()) {
            pageSize = args.get("pageSize").asInt(DEFAULT_PAGE_SIZE);
        }
        if (pageSize < 1) {
            return 1;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }
}

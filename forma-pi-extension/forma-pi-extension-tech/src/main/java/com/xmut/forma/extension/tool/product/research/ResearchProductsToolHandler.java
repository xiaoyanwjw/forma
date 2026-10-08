package com.xmut.forma.extension.tool.product.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.common.web.fetch.WebFetchUrls;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Pi business tool {@code research_products}: enrich recall candidates via 抓取
 * {@link PageFetchPort} only (never crawl).
 */
public final class ResearchProductsToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ResearchProductsToolHandler.class);

    public static final String TOOL_NAME = "research_products";
    public static final int DEFAULT_DEEP_FETCH = 1;
    public static final int MAX_DEEP_FETCH = 1;
    private static final int EVIDENCE_MAX_CHARS = 280;

    private final PageFetchPort pageFetchPort;
    private final ObjectMapper objectMapper;

    public ResearchProductsToolHandler(PageFetchPort pageFetchPort) {
        this(pageFetchPort, new ObjectMapper());
    }

    ResearchProductsToolHandler(PageFetchPort pageFetchPort, ObjectMapper objectMapper) {
        this.pageFetchPort = pageFetchPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            JsonNode args = arguments(call);
            List<ObjectNode> candidates = loadCandidates(args, ctx);
            if (candidates.isEmpty()) {
                return ToolResult.failed(callId, TOOL_NAME, "candidates required");
            }
            int deepFetch = resolveDeepFetch(args);
            ArrayNode outCandidates = objectMapper.createArrayNode();
            List<String> uncertainties = new ArrayList<String>();
            int fetched = 0;

            for (int i = 0; i < candidates.size(); i++) {
                ObjectNode row = candidates.get(i).deepCopy();
                String url = textOrEmpty(row, "url");
                if (fetched < deepFetch && StringUtils.hasText(url)) {
                    researchOne(row, url.trim(), uncertainties);
                    fetched++;
                }
                outCandidates.add(row);
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.set("candidates", outCandidates);
            root.put("deepFetch", deepFetch);
            if (!uncertainties.isEmpty()) {
                ArrayNode notes = root.putArray("uncertainties");
                for (String note : uncertainties) {
                    notes.add(note);
                }
            }
            return ToolResult.ok(callId, TOOL_NAME, objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            log.warn("research_products failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "research_products failed");
        }
    }

    private void researchOne(ObjectNode row, String url, List<String> uncertainties) {
        try {
            WebFetchUrls.validatePublicHttpUrl(url);
            WebFetchHit hit = pageFetchPort.fetch(url);
            String evidence = evidenceFrom(hit);
            row.put("evidence", evidence);
            if (!StringUtils.hasText(evidence)) {
                uncertainties.add("empty evidence for " + shortLabel(row, url));
            }
        } catch (Exception ex) {
            log.warn("research_products fetch failed url={} err={}", url, ex.toString());
            row.put("evidence", "");
            uncertainties.add("research failed for " + shortLabel(row, url));
        }
    }

    private List<ObjectNode> loadCandidates(JsonNode args, ToolContext ctx) throws Exception {
        List<ObjectNode> out = new ArrayList<ObjectNode>();
        JsonNode array = args == null ? null : args.get("candidates");
        if (array != null && array.isArray() && array.size() > 0) {
            for (JsonNode node : array) {
                if (node != null && node.isObject()) {
                    out.add((ObjectNode) node);
                }
            }
            return out;
        }

        String pathRel = textOrNull(args, "candidatesPath");
        if (!StringUtils.hasText(pathRel)) {
            return out;
        }
        String workspace = LocalFileSupport.workspace(ctx);
        if (workspace == null) {
            return out;
        }
        Path path = LocalFileSupport.resolveUnder(Paths.get(workspace), pathRel.trim());
        if (!Files.isRegularFile(path)) {
            return out;
        }
        JsonNode fileRoot = objectMapper.readTree(Files.readAllBytes(path));
        JsonNode fileArray = fileRoot;
        if (fileRoot != null && fileRoot.isObject() && fileRoot.has("candidates")) {
            fileArray = fileRoot.get("candidates");
        }
        if (fileArray == null || !fileArray.isArray()) {
            return out;
        }
        for (JsonNode node : fileArray) {
            if (node != null && node.isObject()) {
                out.add((ObjectNode) node.deepCopy());
            }
        }
        return out;
    }

    static int resolveDeepFetch(JsonNode args) {
        if (args == null || !args.has("deepFetch") || args.get("deepFetch").isNull()) {
            return DEFAULT_DEEP_FETCH;
        }
        int value = args.get("deepFetch").asInt(DEFAULT_DEEP_FETCH);
        if (value < 0) {
            return 0;
        }
        return Math.min(value, MAX_DEEP_FETCH);
    }

    static String evidenceFrom(WebFetchHit hit) {
        if (hit == null) {
            return "";
        }
        String text = hit.getText();
        if (StringUtils.hasText(text)) {
            String collapsed = text.replaceAll("\\s+", " ").trim();
            if (collapsed.length() > EVIDENCE_MAX_CHARS) {
                return collapsed.substring(0, EVIDENCE_MAX_CHARS).trim();
            }
            return collapsed;
        }
        return hit.getTitle() == null ? "" : hit.getTitle().trim();
    }

    private static String shortLabel(ObjectNode row, String url) {
        String title = textOrEmpty(row, "title");
        if (StringUtils.hasText(title)) {
            return title;
        }
        return url;
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

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        String text = value.asText(null);
        return text == null ? "" : text;
    }
}

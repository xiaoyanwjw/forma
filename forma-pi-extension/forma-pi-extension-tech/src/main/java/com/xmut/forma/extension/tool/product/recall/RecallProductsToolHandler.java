package com.xmut.forma.extension.tool.product.recall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchCandidate;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchPort;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchSearchProperties;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Pi business tool {@code recall_products}: paste parse or Product Hunt list → candidates.
 */
public final class RecallProductsToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(RecallProductsToolHandler.class);

    public static final String TOOL_NAME = "recall_products";
    public static final int DEFAULT_MAX_CANDIDATES = 12;
    public static final int MAX_MAX_CANDIDATES = 30;

    private static final Pattern MD_LINK = Pattern.compile(
            "\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)");
    private static final Pattern URL = Pattern.compile("(https?://\\S+)");
    private static final Pattern BULLET = Pattern.compile("^[\\s]*[-*•]\\s+|^\\d+[.)]\\s+");

    private final ProductLaunchSearchPort searchPort;
    private final ProductLaunchSearchProperties properties;
    private final ObjectMapper objectMapper;

    public RecallProductsToolHandler(ProductLaunchSearchPort searchPort,
                                     ProductLaunchSearchProperties properties) {
        this(searchPort, properties, new ObjectMapper());
    }

    RecallProductsToolHandler(ProductLaunchSearchPort searchPort,
                              ProductLaunchSearchProperties properties,
                              ObjectMapper objectMapper) {
        this.searchPort = searchPort;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            String topic = extractText(call, "topic");

            int maxCandidates = resolveMaxCandidates(call);
            String actorId = properties.getApify().getActorId();

            // 无 paste：拉 PH 列表（topic 可空 = 当天热门全表，再只做去重截断）
            int fetchLimit = Math.min(Math.max(maxCandidates * 3, 24), 100);
            List<ProductLaunchCandidate> raw = searchPort.search(topic, fetchLimit);
            List<ProductLaunchCandidate> groundings = normalize(raw, topic, maxCandidates);
            LoggerUtils.success(
                    log,
                    RecallProductsToolHandler.class,
                    "handle",
                    NameValue.create("source", "ph"),
                    NameValue.create("actorId", actorId),
                    NameValue.create("rawCount", raw.size()),
                    NameValue.create("filteredCount", groundings.size()),
                    NameValue.create("maxCandidates", maxCandidates));

            if (groundings.isEmpty()) {
                return ToolResult.failed(callId, TOOL_NAME, "recall_products empty candidates");
            }

            return ToolResult.ok(callId, TOOL_NAME, writeCandidates(groundings));
        } catch (Exception ex) {
            String reason = errorReason(ex);
            LoggerUtils.warn(
                    log,
                    RecallProductsToolHandler.class,
                    "handle",
                    reason,
                    NameValue.create("actorId", properties.getApify().getActorId()));
            return ToolResult.failed(callId, TOOL_NAME, "recall_products failed: " + reason);
        }
    }

    static List<ProductLaunchCandidate> normalize(List<ProductLaunchCandidate> raw,
                                                  String topic,
                                                  int maxCandidates) {
        if (raw == null || raw.isEmpty() || maxCandidates < 1) {
            return new ArrayList<ProductLaunchCandidate>();
        }
        List<String> topicTokens = significantTopicTokens(topic);
        List<ProductLaunchCandidate> matched = new ArrayList<ProductLaunchCandidate>();
        for (ProductLaunchCandidate c : raw) {
            if (c == null || !StringUtils.hasText(c.getTitle())) {
                continue;
            }
            if (!matchesTopic(c, topicTokens)) {
                continue;
            }
            matched.add(c);
        }
        List<ProductLaunchCandidate> deduped = dedupe(matched);
        if (deduped.size() <= maxCandidates) {
            return deduped;
        }
        return new ArrayList<ProductLaunchCandidate>(deduped.subList(0, maxCandidates));
    }

    static List<String> significantTopicTokens(String topic) {
        if (!StringUtils.hasText(topic)) {
            return Collections.emptyList();
        }
        String[] parts = topic.trim().split("\\s+");
        List<String> tokens = new ArrayList<String>();
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private static boolean matchesTopic(ProductLaunchCandidate c, List<String> topicTokens) {
        if (topicTokens.isEmpty()) {
            return true;
        }
        String title = c.getTitle() == null ? "" : c.getTitle().toLowerCase(Locale.ROOT);
        String tagline = c.getTagline() == null ? "" : c.getTagline().toLowerCase(Locale.ROOT);
        for (String token : topicTokens) {
            if (containsTokenWord(title, token) || containsTokenWord(tagline, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTokenWord(String haystack, String token) {
        if (!StringUtils.hasText(haystack) || !StringUtils.hasText(token)) {
            return false;
        }
        int idx = 0;
        while (idx <= haystack.length() - token.length()) {
            int found = haystack.indexOf(token, idx);
            if (found < 0) {
                return false;
            }
            boolean startOk = found == 0 || !Character.isLetterOrDigit(haystack.charAt(found - 1));
            int end = found + token.length();
            boolean endOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            idx = found + 1;
        }
        return false;
    }

    private static List<ProductLaunchCandidate> dedupe(List<ProductLaunchCandidate> items) {
        Set<String> seenUrls = new LinkedHashSet<String>();
        Set<String> seenTitles = new LinkedHashSet<String>();
        List<ProductLaunchCandidate> out = new ArrayList<ProductLaunchCandidate>();
        for (ProductLaunchCandidate c : items) {
            String urlKey = normalizeUrlKey(c.getUrl());
            String titleKey = c.getTitle().trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(urlKey) && seenUrls.contains(urlKey)) {
                continue;
            }
            if (seenTitles.contains(titleKey)) {
                continue;
            }
            if (StringUtils.hasText(urlKey)) {
                seenUrls.add(urlKey);
            }
            seenTitles.add(titleKey);
            out.add(c);
        }
        return out;
    }

    private static String normalizeUrlKey(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String writeCandidates(List<ProductLaunchCandidate> candidates) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode array = root.putArray("candidates");
        for (ProductLaunchCandidate c : candidates) {
            ObjectNode node = array.addObject();
            node.put("title", emptyIfNull(c.getTitle()));
            node.put("tagline", emptyIfNull(c.getTagline()));
            node.put("url", emptyIfNull(c.getUrl()));
            if (c.getVotes() != null) {
                node.put("votes", c.getVotes().intValue());
            }
            if (StringUtils.hasText(c.getPublishedAt())) {
                node.put("publishedAt", c.getPublishedAt());
            }
            node.put("source", StringUtils.hasText(c.getSource()) ? c.getSource() : "ph");
        }
        return objectMapper.writeValueAsString(root);
    }

    static String extractText(ToolCallEntry call, String field) {
        JsonNode args = arguments(call);
        if (args == null) {
            return null;
        }
        JsonNode node = args.get(field);
        return node == null || node.isNull() ? null : node.asText(null);
    }

    static int resolveMaxCandidates(ToolCallEntry call) {
        JsonNode args = arguments(call);
        if (args == null || !args.has("maxCandidates") || args.get("maxCandidates").isNull()) {
            return DEFAULT_MAX_CANDIDATES;
        }
        int value = args.get("maxCandidates").asInt(DEFAULT_MAX_CANDIDATES);
        if (value < 1) {
            return DEFAULT_MAX_CANDIDATES;
        }
        return Math.min(value, MAX_MAX_CANDIDATES);
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }

    private static String errorReason(Throwable ex) {
        if (ex == null || ex.getMessage() == null) {
            return "apify_error";
        }
        String msg = ex.getMessage();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("missing_token")) {
            return "missing_token";
        }
        if (lower.contains("empty")) {
            return msg;
        }
        return msg;
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }

    private static int indexOfSeparator(String text) {
        int em = text.indexOf('—');
        int en = text.indexOf('–');
        int colon = text.indexOf(':');
        int dash = text.indexOf(" - ");
        int best = -1;
        if (em >= 0) {
            best = em;
        }
        if (en >= 0 && (best < 0 || en < best)) {
            best = en;
        }
        if (dash >= 0 && (best < 0 || dash < best)) {
            best = dash;
        }
        if (colon >= 0 && (best < 0 || colon < best)) {
            best = colon;
        }
        return best;
    }

    private static String stripLeadingSeparator(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.trim();
        while (t.startsWith("—") || t.startsWith("–") || t.startsWith("-") || t.startsWith(":")) {
            t = t.substring(1).trim();
        }
        return t;
    }

    private static String stripTrailingSeparator(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String t = text.trim();
        while (t.endsWith("—") || t.endsWith("–") || t.endsWith("-") || t.endsWith(":")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}

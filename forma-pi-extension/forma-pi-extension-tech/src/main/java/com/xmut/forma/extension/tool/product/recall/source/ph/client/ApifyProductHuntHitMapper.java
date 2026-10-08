package com.xmut.forma.extension.tool.product.recall.source.ph.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xmut.forma.extension.tool.product.recall.source.ph.port.ProductLaunchCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * Maps Apify Product Hunt dataset rows to {@link ProductLaunchCandidate}.
 * Tolerates cloud9 / runtime / generic field names.
 */
public final class ApifyProductHuntHitMapper {

    public static final String SOURCE_PH = "ph";

    private ApifyProductHuntHitMapper() {
    }

    public static List<ProductLaunchCandidate> mapItems(ArrayNode items) {
        if (items == null || items.size() == 0) {
            return Collections.emptyList();
        }
        List<ProductLaunchCandidate> out = new ArrayList<ProductLaunchCandidate>(items.size());
        for (JsonNode row : items) {
            Optional<ProductLaunchCandidate> mapped = mapRow(row);
            if (mapped.isPresent()) {
                out.add(mapped.get());
            }
        }
        return Collections.unmodifiableList(out);
    }

    static Optional<ProductLaunchCandidate> mapRow(JsonNode row) {
        if (row == null || !row.isObject()) {
            return Optional.empty();
        }
        String title = firstText(row, "name", "title", "productName");
        if (!StringUtils.hasText(title)) {
            return Optional.empty();
        }
        String tagline = emptyIfNull(firstText(row, "tagline", "description", "tagLine", "subtitle"));
        String url = emptyIfNull(firstText(row, "productUrl", "url", "link", "productHuntUrl", "phUrl"));
        Integer votes = firstInteger(row, "votesCount", "votes", "upvotes", "upvoteCount");
        String publishedAt = emptyIfNull(firstText(row, "launchDate", "publishedAt", "createdAt", "date"));
        return Optional.of(new ProductLaunchCandidate(
                title.trim(),
                tagline,
                url,
                votes,
                publishedAt,
                SOURCE_PH));
    }

    private static String firstText(JsonNode row, String... fields) {
        if (row == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String value = textOrNull(row, field);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Integer firstInteger(JsonNode row, String... fields) {
        if (row == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = row.get(field);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isNumber()) {
                return Integer.valueOf(node.asInt());
            }
            if (node.isTextual()) {
                String text = node.asText().trim();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                try {
                    return Integer.valueOf(Integer.parseInt(text.replace(",", "")));
                } catch (NumberFormatException ignored) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.asText();
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}

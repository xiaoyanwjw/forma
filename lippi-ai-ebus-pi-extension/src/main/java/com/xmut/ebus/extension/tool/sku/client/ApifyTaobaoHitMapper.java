package com.xmut.ebus.extension.tool.sku.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xmut.ebus.extension.tool.sku.port.SkuSearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Maps Apify taobao-search-scraper dataset rows to {@link SkuSearchHit}.
 */
public final class ApifyTaobaoHitMapper {

    static final String PLATFORM = "taobao_apify";

    private ApifyTaobaoHitMapper() {
    }

    public static List<SkuSearchHit> mapItems(ArrayNode items) {
        if (items == null || items.size() == 0) {
            return Collections.emptyList();
        }
        List<SkuSearchHit> hits = new ArrayList<SkuSearchHit>(items.size());
        for (JsonNode row : items) {
            mapRow(row).ifPresent(hits::add);
        }
        return Collections.unmodifiableList(hits);
    }

    static java.util.Optional<SkuSearchHit> mapRow(JsonNode row) {
        if (row == null || !row.isObject()) {
            return java.util.Optional.empty();
        }
        String detailUrl = textOrNull(row, "url");
        if (!StringUtils.hasText(detailUrl) || !detailUrl.startsWith("https://")) {
            return java.util.Optional.empty();
        }
        String titleOriginal = textOrNull(row, "titleOriginal");
        String title = StringUtils.hasText(titleOriginal) ? titleOriginal.trim() : textOrNull(row, "title");
        if (!StringUtils.hasText(title)) {
            return java.util.Optional.empty();
        }
        title = truncate(title.trim(), 120);
        String price = priceAsString(row);
        String category = truncate(resolveCategory(row), 80);
        String rawRef = itemIdAsString(row);
        return java.util.Optional.of(new SkuSearchHit(
                PLATFORM,
                title.trim(),
                price,
                category,
                detailUrl.trim(),
                rawRef));
    }

    private static String resolveCategory(JsonNode row) {
        JsonNode crumbs = row.get("categoryCrumbs");
        if (crumbs != null && crumbs.isArray() && crumbs.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode crumb : crumbs) {
                if (crumb == null || !crumb.isTextual()) {
                    continue;
                }
                String part = crumb.asText().trim();
                if (!StringUtils.hasText(part)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(part);
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        String categoryName = textOrNull(row, "categoryName");
        if (StringUtils.hasText(categoryName)) {
            return categoryName.trim();
        }
        String categoryId = nodeAsString(row.get("categoryId"));
        if (StringUtils.hasText(categoryId)) {
            return "cat:" + categoryId;
        }
        return "";
    }

    private static String priceAsString(JsonNode row) {
        JsonNode price = row.get("price");
        if (price == null || price.isNull()) {
            price = row.get("priceFromSearch");
        }
        if (price == null || price.isNull()) {
            return "";
        }
        if (price.isNumber()) {
            return price.asText();
        }
        if (price.isTextual()) {
            return price.asText().trim();
        }
        return price.toString();
    }

    private static String itemIdAsString(JsonNode row) {
        return nodeAsString(row != null ? row.get("itemId") : null);
    }

    private static String nodeAsString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        return node.asText();
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

    private static String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value) || maxChars < 1 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}

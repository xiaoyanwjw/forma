package com.xmut.forma.extension.tool.web.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import org.springframework.util.StringUtils;

/**
 * Maps the first Apify website-content-crawler dataset row to {@link WebFetchHit}.
 */
public final class ApifyWccHitMapper {

    static final int MAX_TEXT_CHARS = 32000;
    static final int MIN_BODY_CHARS = 400;

    private ApifyWccHitMapper() {
    }

    public static WebFetchHit mapFirst(ArrayNode items) {
        if (items == null || items.size() == 0) {
            throw new IllegalStateException("apify_error: empty dataset");
        }
        JsonNode row = items.get(0);
        if (row == null || !row.isObject()) {
            throw new IllegalStateException("apify_error: expected dataset object");
        }
        String finalUrl = textOf(row.get("url")).trim();
        String title = resolveTitle(row);
        String text = textOf(row.get("text"));
        if (!StringUtils.hasText(text)) {
            text = textOf(row.get("markdown"));
        }
        boolean truncated = false;
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
            truncated = true;
        }
        if (text.replaceAll("\\s", "").length() < MIN_BODY_CHARS) {
            throw new IllegalStateException("empty_body");
        }
        return new WebFetchHit(finalUrl, title, text, truncated);
    }

    private static String resolveTitle(JsonNode row) {
        JsonNode metadata = row.get("metadata");
        if (metadata != null && metadata.isObject()) {
            String title = textOf(metadata.get("title")).trim();
            if (StringUtils.hasText(title)) {
                return title;
            }
        }
        return "untitled";
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText();
        return value == null ? "" : value;
    }
}

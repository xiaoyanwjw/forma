package com.xmut.ebus.application.business.agent.tool.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Maps Apify Xiaohongshu note-detail dataset rows to {@link XhsNoteFetchHit}.
 */
public final class ApifyXhsNoteFetchMapper {

    private ApifyXhsNoteFetchMapper() {
    }

    public static Optional<XhsNoteFetchHit> mapFirst(ArrayNode items) {
        if (items == null || items.size() == 0) {
            return Optional.empty();
        }
        for (JsonNode row : items) {
            Optional<XhsNoteFetchHit> mapped = mapRow(row);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.empty();
    }

    static Optional<XhsNoteFetchHit> mapRow(JsonNode row) {
        if (row == null || !row.isObject()) {
            return Optional.empty();
        }
        String noteUrl = firstText(row, "noteUrl", "url", "shareUrl", "link", "note_url");
        String title = emptyIfNull(firstText(row, "title", "noteTitle"));
        String body = emptyIfNull(firstText(row, "body", "content", "desc", "description", "text", "note"));
        String author = emptyIfNull(resolveAuthor(row));
        List<String> tags = resolveTags(row);
        if (!StringUtils.hasText(noteUrl) && !StringUtils.hasText(title) && !StringUtils.hasText(body)) {
            return Optional.empty();
        }
        return Optional.of(new XhsNoteFetchHit(
                title,
                body,
                noteUrl == null ? "" : noteUrl.trim(),
                author,
                tags));
    }

    private static String resolveAuthor(JsonNode row) {
        String direct = firstText(row, "author", "nickname", "userName");
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        JsonNode user = row.get("user");
        if (user != null && user.isObject()) {
            String nested = firstText(user, "nickname", "name", "userName");
            if (StringUtils.hasText(nested)) {
                return nested.trim();
            }
        }
        return "";
    }

    private static List<String> resolveTags(JsonNode row) {
        JsonNode tags = row.get("tags");
        if (tags == null || tags.isNull()) {
            tags = row.get("hashtags");
        }
        if (tags == null || tags.isNull()) {
            return Collections.emptyList();
        }
        if (tags.isArray()) {
            List<String> out = new ArrayList<String>();
            for (JsonNode t : tags) {
                if (t == null || t.isNull()) {
                    continue;
                }
                String text = t.asText();
                if (StringUtils.hasText(text)) {
                    out.add(text.trim());
                }
            }
            return out;
        }
        if (tags.isTextual() && StringUtils.hasText(tags.asText())) {
            String[] parts = tags.asText().split(",");
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < parts.length; i++) {
                if (StringUtils.hasText(parts[i])) {
                    out.add(parts[i].trim());
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    private static String firstText(JsonNode row, String... fields) {
        if (row == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = row.get(field);
            if (node == null || node.isNull()) {
                continue;
            }
            String value = node.asText();
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}

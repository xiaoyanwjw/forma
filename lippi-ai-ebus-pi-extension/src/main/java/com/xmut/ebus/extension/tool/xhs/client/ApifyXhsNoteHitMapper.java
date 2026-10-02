package com.xmut.ebus.extension.tool.xhs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * Maps Apify Xiaohongshu keyword-search dataset rows to {@link XhsNoteSearchHit}.
 */
public final class ApifyXhsNoteHitMapper {

    private ApifyXhsNoteHitMapper() {
    }

    public static List<XhsNoteSearchHit> mapItems(ArrayNode items) {
        if (items == null || items.size() == 0) {
            return Collections.emptyList();
        }
        List<XhsNoteSearchHit> hits = new ArrayList<XhsNoteSearchHit>(items.size());
        for (JsonNode row : items) {
            Optional<XhsNoteSearchHit> mapped = mapRow(row);
            if (mapped.isPresent()) {
                hits.add(mapped.get());
            }
        }
        return Collections.unmodifiableList(hits);
    }

    static Optional<XhsNoteSearchHit> mapRow(JsonNode row) {
        if (row == null || !row.isObject()) {
            return Optional.empty();
        }
        String noteUrl = firstText(row, "noteUrl", "url", "shareUrl", "link", "note_url");
        if (!StringUtils.hasText(noteUrl) || !noteUrl.startsWith("https://")) {
            return Optional.empty();
        }
        String title = emptyIfNull(firstText(row, "title", "noteTitle"));
        String noteId = emptyIfNull(firstText(row, "noteId", "id", "note_id"));
        String desc = emptyIfNull(firstText(row, "desc", "description", "content", "summary"));
        String likedCount = emptyIfNull(firstNumericOrText(row, "likedCount", "likes", "likeCount", "liked_count"));
        String author = emptyIfNull(resolveAuthor(row));
        return Optional.of(new XhsNoteSearchHit(
                noteId,
                title,
                desc,
                noteUrl.trim(),
                likedCount,
                author));
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

    private static String firstNumericOrText(JsonNode row, String... fields) {
        if (row == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = row.get(field);
            String value = nodeAsString(node);
            if (StringUtils.hasText(value)) {
                return value;
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

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}

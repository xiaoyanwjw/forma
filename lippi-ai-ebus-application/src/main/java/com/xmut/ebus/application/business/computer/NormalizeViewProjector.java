package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sanitizes a candidate Computer view: whitelist block types, drop illegal enums.
 */
@Component
public class NormalizeViewProjector implements ComputerViewProjector {

    private static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList(
            "markdown", "note", "list", "media", "section"));
    private static final Set<String> NOTE_TONES = new HashSet<String>(Arrays.asList("mute", "default"));
    private static final Set<String> LINE_EMPHASIS = new HashSet<String>(Arrays.asList("default", "price"));
    private static final Set<String> TAG_TONES = new HashSet<String>(Arrays.asList(
            "neutral", "positive", "caution", "danger", "info", "safe"));

    @Override
    public boolean supports(ViewProjectContext context) {
        return context != null && context.getRawView() != null;
    }

    @Override
    public Map<String, Object> project(ViewProjectContext context) {
        Map<String, Object> raw = context.getRawView();
        Object versionObj = raw.get("version");
        int version = versionObj instanceof Number ? ((Number) versionObj).intValue() : 0;
        if (version == 2) {
            String title = raw.get("title") instanceof String ? ((String) raw.get("title")).trim() : "";
            if (!StringUtils.hasText(title)) {
                title = "draft";
            }
            String format = raw.get("format") instanceof String
                    ? ((String) raw.get("format")).trim().toLowerCase()
                    : "";
            if (!"markdown".equals(format) && !"html".equals(format)) {
                return new LinkedHashMap<String, Object>();
            }
            String content = raw.get("content") instanceof String ? ((String) raw.get("content")) : "";
            if (!StringUtils.hasText(content.trim())) {
                return new LinkedHashMap<String, Object>();
            }
            content = content.replace("\u0000", "");
            return ComputerDocument.v2(title, format, content);
        }
        if (version != 1) {
            return new LinkedHashMap<String, Object>();
        }
        String title = raw.get("title") instanceof String ? ((String) raw.get("title")).trim() : "";
        if (!StringUtils.hasText(title)) {
            title = "draft";
        }
        String status = raw.get("status") instanceof String ? ((String) raw.get("status")).trim() : null;
        if (status != null && status.isEmpty()) {
            status = null;
        }
        List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
        Object blocksObj = raw.get("blocks");
        if (blocksObj instanceof List) {
            for (Object item : (List<?>) blocksObj) {
                Map<String, Object> cleaned = cleanBlock(item);
                if (cleaned != null) {
                    blocks.add(cleaned);
                }
            }
        }
        return new ComputerDocument(1, title, status, blocks).toMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cleanBlock(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<String, Object> block = (Map<String, Object>) raw;
        Object typeObj = block.get("type");
        if (!(typeObj instanceof String) || !BLOCK_TYPES.contains(typeObj)) {
            return null;
        }
        String type = (String) typeObj;
        if ("markdown".equals(type)) {
            if (!(block.get("text") instanceof String)) {
                return null;
            }
            return ComputerBlock.markdown((String) block.get("text"));
        }
        if ("note".equals(type)) {
            if (!(block.get("text") instanceof String)) {
                return null;
            }
            String tone = asAllowedString(block.get("tone"), NOTE_TONES);
            String kind = block.get("kind") instanceof String ? ((String) block.get("kind")).trim() : null;
            return ComputerBlock.note((String) block.get("text"), tone, kind);
        }
        if ("list".equals(type)) {
            return cleanList(block);
        }
        if ("media".equals(type)) {
            return cleanMedia(block);
        }
        if ("section".equals(type)) {
            if (!(block.get("heading") instanceof String) || !(block.get("body") instanceof String)) {
                return null;
            }
            Map<String, Object> section = new LinkedHashMap<String, Object>();
            section.put("type", "section");
            section.put("heading", block.get("heading"));
            section.put("body", block.get("body"));
            String tone = asAllowedString(block.get("tone"), NOTE_TONES);
            if (tone != null) {
                section.put("tone", tone);
            }
            return section;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cleanList(Map<String, Object> block) {
        Object itemsObj = block.get("items");
        if (!(itemsObj instanceof List)) {
            return null;
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Object row : (List<?>) itemsObj) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) row;
            if (!(item.get("title") instanceof String)) {
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("title", item.get("title"));
            String id = asNonBlankString(item.get("id"));
            if (id != null) {
                out.put("id", id);
            }
            if (item.get("badge") instanceof String) {
                out.put("badge", item.get("badge"));
            }
            List<Map<String, Object>> lines = cleanLines(item.get("lines"));
            if (lines != null && !lines.isEmpty()) {
                out.put("lines", lines);
            }
            List<Map<String, Object>> tags = cleanTags(item.get("tags"));
            if (tags != null && !tags.isEmpty()) {
                out.put("tags", tags);
            }
            String href = asHttpsHref(item.get("href"));
            if (href != null) {
                out.put("href", href);
            }
            items.add(out);
        }
        boolean ordered = !(block.get("ordered") instanceof Boolean) || Boolean.TRUE.equals(block.get("ordered"));
        return ComputerBlock.list(ordered, items);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cleanLines(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object row : (List<?>) raw) {
            if (row instanceof String && StringUtils.hasText((String) row)) {
                Map<String, Object> line = new LinkedHashMap<String, Object>();
                line.put("text", ((String) row).trim());
                out.add(line);
                continue;
            }
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> line = (Map<String, Object>) row;
            if (!(line.get("text") instanceof String) || !StringUtils.hasText((String) line.get("text"))) {
                continue;
            }
            Map<String, Object> cleaned = new LinkedHashMap<String, Object>();
            cleaned.put("text", ((String) line.get("text")).trim());
            if (line.get("kind") instanceof String && StringUtils.hasText((String) line.get("kind"))) {
                cleaned.put("kind", ((String) line.get("kind")).trim());
            }
            if (line.get("label") instanceof String && StringUtils.hasText((String) line.get("label"))) {
                cleaned.put("label", ((String) line.get("label")).trim());
            }
            String emphasis = asAllowedString(line.get("emphasis"), LINE_EMPHASIS);
            if (emphasis != null) {
                cleaned.put("emphasis", emphasis);
            }
            out.add(cleaned);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cleanTags(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object row : (List<?>) raw) {
            if (row instanceof String && StringUtils.hasText((String) row)) {
                Map<String, Object> tag = new LinkedHashMap<String, Object>();
                tag.put("text", ((String) row).trim());
                tag.put("tone", "neutral");
                out.add(tag);
                continue;
            }
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> tag = (Map<String, Object>) row;
            if (!(tag.get("text") instanceof String) || !StringUtils.hasText((String) tag.get("text"))) {
                continue;
            }
            Map<String, Object> cleaned = new LinkedHashMap<String, Object>();
            cleaned.put("text", ((String) tag.get("text")).trim());
            if (tag.get("kind") instanceof String && StringUtils.hasText((String) tag.get("kind"))) {
                cleaned.put("kind", ((String) tag.get("kind")).trim());
            }
            if (tag.get("label") instanceof String && StringUtils.hasText((String) tag.get("label"))) {
                cleaned.put("label", ((String) tag.get("label")).trim());
            }
            String tone = asAllowedString(tag.get("tone"), TAG_TONES);
            cleaned.put("tone", tone != null ? tone : "neutral");
            out.add(cleaned);
        }
        return out;
    }

    private static Map<String, Object> cleanMedia(Map<String, Object> block) {
        Map<String, Object> media = new LinkedHashMap<String, Object>();
        media.put("type", "media");
        if ("hero".equals(block.get("role"))) {
            media.put("role", "hero");
        }
        copyString(block, media, "alt");
        copyString(block, media, "src");
        copyString(block, media, "mediaObjectId");
        copyString(block, media, "placeholder");
        return media;
    }

    private static void copyString(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.get(key) instanceof String) {
            to.put(key, from.get(key));
        }
    }

    private static String asAllowedString(Object value, Set<String> allowed) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return allowed.contains(text) ? text : null;
    }

    private static String asHttpsHref(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        if (!text.startsWith("https://")) {
            return null;
        }
        return text;
    }

    private static String asNonBlankString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}

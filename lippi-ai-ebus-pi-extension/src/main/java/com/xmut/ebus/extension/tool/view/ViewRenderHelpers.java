package com.xmut.ebus.extension.tool.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * In-memory display fields for skill templates (not persisted to artifact.json).
 */
public final class ViewRenderHelpers {

    private static final String PRIORITY_TOPIC = "【优先发】";
    private static final String PRIORITY_PICK = "【优先试】";
    private static final int BREAK_STRUCTURE_MAX = 240;
    private static final int BREAK_TEXT_MAX = 400;

    private ViewRenderHelpers() {
    }

    public static Map<String, Object> enrich(String skillId, Map<String, Object> artifact) {
        Map<String, Object> source = artifact == null
                ? new LinkedHashMap<String, Object>()
                : artifact;
        if (!StringUtils.hasText(skillId)) {
            return source;
        }
        if ("xhs-topiclist".equals(skillId)) {
            return enrichTopicList(deepCopy(source));
        }
        if ("ecommerce-picklist".equals(skillId)) {
            return enrichPickList(deepCopy(source));
        }
        if ("xhs-break".equals(skillId)) {
            return enrichBreak(deepCopy(source));
        }
        if ("xhs-note".equals(skillId)) {
            return enrichNote(deepCopy(source));
        }
        if ("ecommerce-skulist".equals(skillId)) {
            return enrichSkulist(deepCopy(source));
        }
        return source;
    }

    private static Map<String, Object> enrichTopicList(Map<String, Object> artifact) {
        enrichItems(artifact, ViewRenderHelpers::topicItemHelpers);
        return artifact;
    }

    private static Map<String, Object> enrichPickList(Map<String, Object> artifact) {
        enrichItems(artifact, ViewRenderHelpers::pickItemHelpers);
        return artifact;
    }

    private static Map<String, Object> enrichBreak(Map<String, Object> artifact) {
        String handoff = buildXhsBreakNoteHandoffText(
                trimString(artifact.get("targetProduct")),
                trimString(artifact.get("structure")),
                trimString(artifact.get("skeleton")),
                trimString(artifact.get("rewrite")));
        if (handoff != null) {
            artifact.put("handoffPrompt", handoff);
        }
        return artifact;
    }

    private static Map<String, Object> enrichNote(Map<String, Object> artifact) {
        putNumberedLines(artifact, "titleOptions", "titleOptionsNumbered");
        putNumberedLines(artifact, "imageHints", "imageHintsNumbered");
        Object tags = artifact.get("tags");
        if (tags instanceof List) {
            List<?> list = (List<?>) tags;
            List<String> parts = new ArrayList<String>(list.size());
            for (Object entry : list) {
                String trimmed = trimString(entry);
                if (StringUtils.hasText(trimmed)) {
                    parts.add(trimmed);
                }
            }
            if (!parts.isEmpty()) {
                artifact.put("tagsDisplay", joinWithMiddleDot(parts));
            }
        }
        return artifact;
    }

    private static Map<String, Object> enrichSkulist(Map<String, Object> artifact) {
        String detailBody = trimString(artifact.get("detailBody"));
        if (StringUtils.hasText(detailBody)) {
            artifact.put("detailParagraphs", splitParagraphs(detailBody));
        }
        zipFramePrompts(artifact);
        return artifact;
    }

    private static void putNumberedLines(
            Map<String, Object> artifact,
            String sourceKey,
            String targetKey) {
        Object raw = artifact.get(sourceKey);
        if (!(raw instanceof List)) {
            return;
        }
        List<?> list = (List<?>) raw;
        List<String> numbered = new ArrayList<String>(list.size());
        int index = 1;
        for (Object entry : list) {
            String trimmed = trimString(entry);
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            numbered.add(index + ". " + trimmed);
            index++;
        }
        if (!numbered.isEmpty()) {
            artifact.put(targetKey, numbered);
        }
    }

    private static void zipFramePrompts(Map<String, Object> artifact) {
        Object rawFrames = artifact.get("frames");
        Object rawPrompts = artifact.get("framePrompts");
        if (!(rawFrames instanceof List) || !(rawPrompts instanceof List)) {
            return;
        }
        List<?> frames = (List<?>) rawFrames;
        List<?> prompts = (List<?>) rawPrompts;
        int size = Math.min(frames.size(), prompts.size());
        if (size == 0) {
            return;
        }
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>(size);
        for (int i = 0; i < size; i++) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            String frame = trimString(frames.get(i));
            if (StringUtils.hasText(frame)) {
                entry.put("frame", frame);
            }
            Object promptObj = prompts.get(i);
            if (promptObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> promptMap = (Map<String, Object>) promptObj;
                String prompt = trimString(promptMap.get("prompt"));
                if (StringUtils.hasText(prompt)) {
                    entry.put("prompt", prompt);
                }
                String negative = trimString(promptMap.get("negative"));
                if (StringUtils.hasText(negative)) {
                    entry.put("negative", negative);
                }
            }
            entries.add(entry);
        }
        artifact.put("frameEntries", entries);
    }

    private static List<String> splitParagraphs(String body) {
        String normalized = body.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return new ArrayList<String>();
        }
        String[] lines = normalized.split("\n");
        List<String> paragraphs = new ArrayList<String>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private static String joinWithMiddleDot(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static void enrichItems(Map<String, Object> artifact, ItemEnricher enricher) {
        Object rawItems = artifact.get("items");
        if (!(rawItems instanceof List)) {
            return;
        }
        List<?> list = (List<?>) rawItems;
        List<Map<String, Object>> enriched = new ArrayList<Map<String, Object>>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = deepCopy((Map<String, Object>) entry);
            enricher.apply(item);
            enriched.add(item);
        }
        artifact.put("items", enriched);
    }

    private static void topicItemHelpers(Map<String, Object> item) {
        String title = stripPrefix(trimString(item.get("title")), PRIORITY_TOPIC);
        if (StringUtils.hasText(title)) {
            item.put("displayTitle", title);
        }
        String handoff = buildXhsNoteHandoffText(
                title,
                trimString(item.get("id")),
                firstHttpsUrl(item.get("sourceNoteUrl"), item.get("href")),
                trimString(item.get("angle")),
                trimString(item.get("hook")));
        if (handoff != null) {
            item.put("handoffPrompt", handoff);
        }
    }

    private static void pickItemHelpers(Map<String, Object> item) {
        String title = stripAll(trimString(item.get("title")), PRIORITY_PICK);
        if (StringUtils.hasText(title)) {
            item.put("displayTitle", title);
        }
        String handoff = buildListingHandoffText(
                title,
                trimString(item.get("id")),
                firstHttpsUrl(item.get("sourceUrl"), item.get("href")),
                trimString(item.get("niche")),
                trimString(item.get("painPoint")),
                trimString(item.get("angle")));
        if (handoff != null) {
            item.put("handoffPrompt", handoff);
        }
    }

    static String buildXhsNoteHandoffText(
            String title,
            String id,
            String href,
            String angle,
            String hook) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(id)) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(String.format(
                "请根据选题「%s」（条目 %s）写一篇小红书种草笔记，语气像真人分享。",
                title,
                id));
        if (StringUtils.hasText(angle)) {
            lines.add("角度：" + angle);
        }
        if (StringUtils.hasText(hook)) {
            lines.add("钩子：" + hook);
        }
        if (StringUtils.hasText(href)) {
            lines.add("原笔记：" + href);
        }
        return joinLines(lines);
    }

    static String buildXhsBreakNoteHandoffText(
            String targetProduct,
            String structure,
            String skeleton,
            String rewrite) {
        List<String> lines = new ArrayList<String>();
        if (StringUtils.hasText(targetProduct)) {
            lines.add(String.format(
                    "请按这次爆文拆解的骨架，写一篇关于「%s」的小红书种草笔记，语气像真人分享。",
                    targetProduct.trim()));
        } else {
            lines.add("请按这次爆文拆解的骨架写一篇小红书种草笔记，语气像真人分享。");
        }
        structure = truncate(trimString(structure), BREAK_STRUCTURE_MAX);
        skeleton = truncate(trimString(skeleton), BREAK_TEXT_MAX);
        rewrite = truncate(trimString(rewrite), BREAK_TEXT_MAX);
        if (StringUtils.hasText(structure)) {
            lines.add("结构要点：" + structure);
        }
        if (StringUtils.hasText(skeleton)) {
            lines.add("骨架：" + skeleton);
        }
        if (StringUtils.hasText(rewrite)) {
            lines.add("改写参考：" + rewrite);
        }
        return joinLines(lines);
    }

    static String buildListingHandoffText(
            String title,
            String id,
            String href,
            String niche,
            String painPoint,
            String angle) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(id)) {
            return null;
        }
        if (!StringUtils.hasText(href) || !href.toLowerCase().startsWith("https://")) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(String.format("请为商品「%s」生成上架素材。", title));
        lines.add("原链：" + href);
        lines.add("来源选品条目：" + id);
        if (StringUtils.hasText(niche)) {
            lines.add("参考：" + niche);
        }
        if (StringUtils.hasText(painPoint)) {
            lines.add("痛点：" + painPoint);
        }
        if (StringUtils.hasText(angle)) {
            lines.add("角度：" + angle);
        }
        return joinLines(lines);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    private static String firstHttpsUrl(Object... candidates) {
        for (Object candidate : candidates) {
            String trimmed = trimString(candidate);
            if (StringUtils.hasText(trimmed) && trimmed.toLowerCase().startsWith("https://")) {
                return trimmed;
            }
        }
        return null;
    }

    private static String stripPrefix(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(prefix)) {
            return trimmed.substring(prefix.length()).trim();
        }
        return trimmed;
    }

    private static String stripAll(String value, String mark) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.replace(mark, "").trim();
    }

    private static String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "…";
    }

    private static String trimString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return deepCopy(map);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> copy = new ArrayList<Object>(list.size());
            for (Object element : list) {
                copy.add(deepCopyValue(element));
            }
            return copy;
        }
        return value;
    }

    @FunctionalInterface
    private interface ItemEnricher {
        void apply(Map<String, Object> item);
    }
}

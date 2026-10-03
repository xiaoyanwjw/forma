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

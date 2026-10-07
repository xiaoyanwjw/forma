package com.xmut.forma.extension.tool.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Shared in-memory display helpers for skill view enrichers (not persisted to artifact.json).
 *
 * <p>Skill 分发见 {@link ViewEnricherComposite}；场景实现 {@link ViewEnricher}。
 */
public final class ViewRenderHelpers {

    static final String PRIORITY_TOPIC = "【优先发】";
    static final String PRIORITY_PICK = "【优先试】";
    static final int BREAK_STRUCTURE_MAX = 240;
    static final int BREAK_TEXT_MAX = 400;

    private ViewRenderHelpers() {
    }

    static void putDefaultEmptyList(Map<String, Object> artifact, String key) {
        Object raw = artifact.get(key);
        if (!(raw instanceof List)) {
            artifact.put(key, new ArrayList<Object>());
        }
    }

    static void putItemCount(Map<String, Object> artifact) {
        Object rawItems = artifact.get("items");
        if (rawItems instanceof List) {
            artifact.put("itemCount", Integer.valueOf(((List<?>) rawItems).size()));
        }
    }

    static void putNumberedLines(
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

    static void zipFramePrompts(Map<String, Object> artifact) {
        Object rawFrames = artifact.get("frames");
        if (!(rawFrames instanceof List)) {
            return;
        }
        List<?> frames = (List<?>) rawFrames;
        Object rawPrompts = artifact.get("framePrompts");
        List<?> prompts = rawPrompts instanceof List ? (List<?>) rawPrompts : null;
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            String frame = trimString(frames.get(i));
            if (StringUtils.hasText(frame)) {
                entry.put("frame", frame);
            }
            entry.put("indexLabel", padIndex(i + 1));
            if (i == 0) {
                entry.put("isFirst", Boolean.TRUE);
            }
            if (prompts != null && i < prompts.size()) {
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
            }
            entries.add(entry);
        }
        if (!entries.isEmpty()) {
            artifact.put("frameEntries", entries);
        }
    }

    static List<String> splitParagraphs(String body) {
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

    static String joinWithMiddleDot(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    static void enrichItems(Map<String, Object> artifact, ItemEnricher enricher) {
        Object rawItems = artifact.get("items");
        if (!(rawItems instanceof List)) {
            return;
        }
        List<?> list = (List<?>) rawItems;
        List<Map<String, Object>> enriched = new ArrayList<Map<String, Object>>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = deepCopy((Map<String, Object>) entry);
            item.put("indexLabel", padIndex(i + 1));
            if (i == 0) {
                item.put("detailsOpen", Boolean.TRUE);
            }
            enricher.apply(item);
            enriched.add(item);
        }
        artifact.put("items", enriched);
    }

    static void topicItemHelpers(Map<String, Object> item) {
        String rawTitle = trimString(item.get("title"));
        boolean priority = StringUtils.hasText(rawTitle) && rawTitle.contains(PRIORITY_TOPIC);
        String title = stripPrefix(rawTitle, PRIORITY_TOPIC);
        if (StringUtils.hasText(title)) {
            item.put("displayTitle", title);
        }
        if (priority) {
            item.put("priorityPublish", Boolean.TRUE);
            item.put("priorityLabel", "优先发");
            item.put("priorityClass", "pink");
        } else {
            item.put("priorityLabel", "备选");
            item.put("priorityClass", "gray");
        }
        putToneField(item, "risk", "riskLabel", "riskClass", "risk");
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

    static void pickItemHelpers(Map<String, Object> item) {
        String rawTitle = trimString(item.get("title"));
        boolean priority = StringUtils.hasText(rawTitle) && rawTitle.contains(PRIORITY_PICK);
        String title = stripAll(rawTitle, PRIORITY_PICK);
        if (StringUtils.hasText(title)) {
            item.put("displayTitle", title);
        }
        if (priority) {
            item.put("priorityTry", Boolean.TRUE);
        }
        putToneField(item, "demand", "demandLabel", "demandClass", "demand");
        putToneField(item, "competition", "competitionLabel", "competitionClass", "competition");
        putToneField(item, "margin", "marginLabel", "marginClass", "margin");
        putToneField(item, "risk", "riskLabel", "riskClass", "risk");
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

    static void putToneField(
            Map<String, Object> item,
            String sourceKey,
            String labelKey,
            String classKey,
            String toneKind) {
        String raw = trimString(item.get(sourceKey));
        if (!StringUtils.hasText(raw)) {
            return;
        }
        String label = scoreLabel(raw);
        item.put(labelKey, label);
        item.put(classKey, scoreTone(toneKind, label));
    }

    static String scoreLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String trimmed = raw.trim();
        int pipe = indexOfPipe(trimmed);
        if (pipe > 0) {
            return trimmed.substring(0, pipe).trim();
        }
        return trimmed.length() <= 4 ? trimmed : trimmed.substring(0, 4);
    }

    static String scoreTone(String kind, String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if ("competition".equals(kind)) {
            if (containsAny(normalized, "高", "偏高")) {
                return "warn";
            }
            if (containsAny(normalized, "低")) {
                return "gray";
            }
            return "mid";
        }
        if ("risk".equals(kind)) {
            if (containsAny(normalized, "高", "硬广", "同质")) {
                return containsAny(normalized, "高") ? "bad" : "mid";
            }
            if (containsAny(normalized, "低")) {
                return "gray";
            }
            return "mid";
        }
        // demand / margin / default: higher is greener
        if (containsAny(normalized, "高", "中高")) {
            return "ok";
        }
        if (containsAny(normalized, "低")) {
            return "gray";
        }
        return "mid";
    }

    static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static int indexOfPipe(String value) {
        int full = value.indexOf('｜');
        int half = value.indexOf('|');
        if (full < 0) {
            return half;
        }
        if (half < 0) {
            return full;
        }
        return Math.min(full, half);
    }

    static String padIndex(int index) {
        return index < 10 ? "0" + index : String.valueOf(index);
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

    static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    static String firstHttpsUrl(Object... candidates) {
        for (Object candidate : candidates) {
            String trimmed = trimString(candidate);
            if (StringUtils.hasText(trimmed) && trimmed.toLowerCase().startsWith("https://")) {
                return trimmed;
            }
        }
        return null;
    }

    static String stripPrefix(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(prefix)) {
            return trimmed.substring(prefix.length()).trim();
        }
        return trimmed;
    }

    static String stripAll(String value, String mark) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.replace(mark, "").trim();
    }

    static String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "…";
    }

    static String trimString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
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
    interface ItemEnricher {
        void apply(Map<String, Object> item);
    }
}

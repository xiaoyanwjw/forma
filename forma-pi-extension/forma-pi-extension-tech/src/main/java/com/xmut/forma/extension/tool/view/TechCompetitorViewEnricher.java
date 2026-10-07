package com.xmut.forma.extension.tool.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * tech-competitor view enricher：三态标签与分层展示字段。
 */
public final class TechCompetitorViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "tech-competitor";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.putDefaultEmptyList(artifact, "excerpts");
        ViewRenderHelpers.putDefaultEmptyList(artifact, "uncertainties");

        Map<String, Object> snapshot = asMap(artifact.get("snapshot"));
        artifact.put("snapshot", snapshot);
        enrichField(snapshot, "positioning", "未写明定位");
        enrichField(snapshot, "audience", "未写明人群");
        enrichField(snapshot, "pricingSignal", "未见公开定价信号");

        Map<String, Object> whyPay = asMap(artifact.get("whyPay"));
        artifact.put("whyPay", whyPay);
        applyStatusLabels(whyPay);
        List<String> whyBullets = stringList(whyPay.get("bullets"));
        whyPay.put("bullets", whyBullets);
        whyPay.put("hasBullets", Boolean.valueOf(!whyBullets.isEmpty()));
        if (whyBullets.isEmpty()) {
            String reason = ViewRenderHelpers.trimString(whyPay.get("reason"));
            whyPay.put("emptyNote", StringUtils.hasText(reason) ? reason : "材料不足");
        }

        Map<String, Object> packaging = asMap(artifact.get("packaging"));
        artifact.put("packaging", packaging);
        applyStatusLabels(packaging);
        String packValue = ViewRenderHelpers.trimString(packaging.get("value"));
        if (!StringUtils.hasText(packValue)) {
            String reason = ViewRenderHelpers.trimString(packaging.get("reason"));
            packaging.put("valueDisplay", StringUtils.hasText(reason) ? reason : "未见公开套餐");
        } else {
            packaging.put("valueDisplay", packValue);
        }

        Map<String, Object> growth = asMap(artifact.get("growthSignals"));
        artifact.put("growthSignals", growth);
        applyStatusLabels(growth);
        List<String> growthItems = stringList(growth.get("items"));
        growth.put("items", growthItems);
        growth.put("hasItems", Boolean.valueOf(!growthItems.isEmpty()));
        String growthStatus = ViewRenderHelpers.trimString(growth.get("status")).toLowerCase(Locale.ROOT);
        boolean showGrowth = !"not_public".equals(growthStatus) || !growthItems.isEmpty();
        growth.put("showSection", Boolean.valueOf(showGrowth));
        if (growthItems.isEmpty()) {
            String reason = ViewRenderHelpers.trimString(growth.get("reason"));
            growth.put("emptyNote", StringUtils.hasText(reason) ? reason : "无公开增长信号");
        }

        Map<String, Object> rivals = asMap(artifact.get("rivals"));
        artifact.put("rivals", rivals);
        applyStatusLabels(rivals);
        if (StringUtils.hasText(ViewRenderHelpers.trimString(rivals.get("reason")))) {
            rivals.put("hasReason", Boolean.TRUE);
        }
        List<Map<String, Object>> rivalItems = new ArrayList<Map<String, Object>>();
        Object rawItems = rivals.get("items");
        if (rawItems instanceof List) {
            List<?> list = (List<?>) rawItems;
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> row = ViewRenderHelpers.deepCopy((Map<String, Object>) entry);
                String name = ViewRenderHelpers.trimString(row.get("name"));
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                row.put("name", name);
                if (!StringUtils.hasText(ViewRenderHelpers.trimString(row.get("note")))) {
                    row.put("note", "—");
                }
                row.put("indexLabel", ViewRenderHelpers.padIndex(rivalItems.size() + 1));
                rivalItems.add(row);
            }
        }
        rivals.put("items", rivalItems);
        rivals.put("hasItems", Boolean.valueOf(!rivalItems.isEmpty()));
        artifact.put("rivalCount", String.valueOf(rivalItems.size()));

        List<?> excerptsRaw = (List<?>) artifact.get("excerpts");
        List<Map<String, Object>> excerpts = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < excerptsRaw.size(); i++) {
            Object entry = excerptsRaw.get(i);
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> excerpt = ViewRenderHelpers.deepCopy((Map<String, Object>) entry);
            Object quotesRaw = excerpt.get("quotes");
            int quoteCount = quotesRaw instanceof List ? ((List<?>) quotesRaw).size() : 0;
            excerpt.put("quoteCount", String.valueOf(quoteCount));
            if (excerpts.isEmpty()) {
                excerpt.put("detailsOpen", Boolean.TRUE);
            }
            excerpts.add(excerpt);
        }
        artifact.put("excerpts", excerpts);
        if (!excerpts.isEmpty()) {
            artifact.put("hasExcerpts", Boolean.TRUE);
        }

        List<?> uncertainties = (List<?>) artifact.get("uncertainties");
        if (!uncertainties.isEmpty()) {
            artifact.put("hasUncertainties", Boolean.TRUE);
            artifact.put("uncertaintyCount", String.valueOf(uncertainties.size()));
        }

        String source = ViewRenderHelpers.trimString(artifact.get("source"));
        if ("paste".equalsIgnoreCase(source)) {
            artifact.put("sourceLabel", "粘贴正文");
            artifact.put("sourceClass", "gray");
        } else if ("fetch".equalsIgnoreCase(source)) {
            artifact.put("sourceLabel", "公开链接");
            artifact.put("sourceClass", "info");
        } else {
            artifact.put("sourceLabel", "原文");
            artifact.put("sourceClass", "gray");
        }
        if (StringUtils.hasText(ViewRenderHelpers.trimString(artifact.get("sourceUrl")))) {
            artifact.put("hasSourceUrl", Boolean.TRUE);
        }
        if (StringUtils.hasText(ViewRenderHelpers.trimString(artifact.get("concern")))) {
            artifact.put("hasConcern", Boolean.TRUE);
        }
        return artifact;
    }

    private static void enrichField(Map<String, Object> parent, String key, String emptyDefault) {
        Map<String, Object> field = asMap(parent.get(key));
        parent.put(key, field);
        applyStatusLabels(field);
        String value = ViewRenderHelpers.trimString(field.get("value"));
        if (!StringUtils.hasText(value)) {
            String reason = ViewRenderHelpers.trimString(field.get("reason"));
            field.put("value", StringUtils.hasText(reason) ? reason : emptyDefault);
            field.put("valueDisplay", field.get("value"));
        } else {
            field.put("valueDisplay", value);
        }
    }

    private static void applyStatusLabels(Map<String, Object> node) {
        String status = ViewRenderHelpers.trimString(node.get("status")).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(status)) {
            status = "not_public";
            node.put("status", status);
        }
        if ("found".equals(status)) {
            node.put("statusLabel", "Found");
            node.put("statusClass", "info");
        } else if ("inferred".equals(status)) {
            node.put("statusLabel", "推断");
            node.put("statusClass", "warn");
        } else {
            node.put("statusLabel", "未公开");
            node.put("statusClass", "gray");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map) {
            return ViewRenderHelpers.deepCopy((Map<String, Object>) raw);
        }
        return new LinkedHashMap<String, Object>();
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) raw;
        List<String> out = new ArrayList<String>();
        for (Object item : list) {
            String text = ViewRenderHelpers.trimString(item);
            if (StringUtils.hasText(text)) {
                out.add(text);
            }
        }
        return out;
    }
}

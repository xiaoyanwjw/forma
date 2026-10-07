package com.xmut.forma.extension.tool.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * tech-digest view enricher（场景模块自注册）。
 */
public final class TechDigestViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "tech-digest";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.putDefaultEmptyList(artifact, "points");
        ViewRenderHelpers.putDefaultEmptyList(artifact, "excerpts");
        ViewRenderHelpers.putDefaultEmptyList(artifact, "uncertainties");
        List<?> points = (List<?>) artifact.get("points");
        List<Map<String, Object>> pointItems = new ArrayList<Map<String, Object>>(points.size());
        for (int i = 0; i < points.size(); i++) {
            String text = ViewRenderHelpers.trimString(points.get(i));
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("indexLabel", ViewRenderHelpers.padIndex(pointItems.size() + 1));
            row.put("text", text);
            pointItems.add(row);
        }
        artifact.put("pointItems", pointItems);
        artifact.put("pointCount", String.valueOf(pointItems.size()));

        List<?> excerptsRaw = (List<?>) artifact.get("excerpts");
        List<Map<String, Object>> excerpts = new ArrayList<Map<String, Object>>();
        int quoteTotal = 0;
        for (int i = 0; i < excerptsRaw.size(); i++) {
            Object entry = excerptsRaw.get(i);
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> excerpt = ViewRenderHelpers.deepCopy((Map<String, Object>) entry);
            Object quotesRaw = excerpt.get("quotes");
            int quoteCount = quotesRaw instanceof List ? ((List<?>) quotesRaw).size() : 0;
            quoteTotal += quoteCount;
            excerpt.put("quoteCount", String.valueOf(quoteCount));
            if (excerpts.isEmpty()) {
                excerpt.put("detailsOpen", Boolean.TRUE);
            }
            excerpts.add(excerpt);
        }
        artifact.put("excerpts", excerpts);
        artifact.put("excerptCount", String.valueOf(excerpts.size()));
        artifact.put("quoteCount", String.valueOf(quoteTotal));

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
}

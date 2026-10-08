package com.xmut.forma.extension.tool.product.view;

import com.xmut.forma.extension.tool.view.ViewEnricher;
import com.xmut.forma.extension.tool.view.ViewRenderHelpers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * tech-briefing view enricher：抬头（域 + 时间窗）与条目卡片展示字段。
 */
public final class TechBriefingViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "tech-briefing";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.putDefaultEmptyList(artifact, "items");
        ViewRenderHelpers.putDefaultEmptyList(artifact, "uncertainties");

        String topic = ViewRenderHelpers.trimString(artifact.get("topic"));
        String windowLabel = ViewRenderHelpers.trimString(artifact.get("windowLabel"));
        if (StringUtils.hasText(topic)) {
            artifact.put("topic", topic);
            artifact.put("hasTopic", Boolean.TRUE);
        }
        if (StringUtils.hasText(windowLabel)) {
            artifact.put("windowLabel", windowLabel);
            artifact.put("hasWindowLabel", Boolean.TRUE);
        }

        String title = ViewRenderHelpers.trimString(artifact.get("title"));
        if (!StringUtils.hasText(title)) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(topic)) {
                sb.append(topic);
            }
            if (StringUtils.hasText(windowLabel)) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(windowLabel);
            }
            if (sb.length() == 0) {
                sb.append("产品早报");
            }
            artifact.put("title", sb.toString());
        }

        List<?> rawItems = (List<?>) artifact.get("items");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < rawItems.size(); i++) {
            Object entry = rawItems.get(i);
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> row = ViewRenderHelpers.deepCopy((Map<String, Object>) entry);
            String itemTitle = ViewRenderHelpers.trimString(row.get("title"));
            if (!StringUtils.hasText(itemTitle)) {
                continue;
            }
            row.put("title", itemTitle);
            row.put("indexLabel", ViewRenderHelpers.padIndex(items.size() + 1));
            if (items.isEmpty()) {
                row.put("detailsOpen", Boolean.TRUE);
            }
            String oneLiner = ViewRenderHelpers.trimString(row.get("oneLiner"));
            if (StringUtils.hasText(oneLiner)) {
                row.put("oneLiner", oneLiner);
                row.put("hasOneLiner", Boolean.TRUE);
            }
            String whyNow = ViewRenderHelpers.trimString(row.get("whyNow"));
            if (StringUtils.hasText(whyNow)) {
                row.put("whyNow", whyNow);
                row.put("hasWhyNow", Boolean.TRUE);
            }
            String forWhom = ViewRenderHelpers.trimString(row.get("forWhom"));
            if (StringUtils.hasText(forWhom)) {
                row.put("forWhom", forWhom);
                row.put("hasForWhom", Boolean.TRUE);
            }
            String sourceUrl = ViewRenderHelpers.firstHttpsUrl(row.get("sourceUrl"));
            if (StringUtils.hasText(sourceUrl)) {
                row.put("sourceUrl", sourceUrl);
                row.put("hasSourceUrl", Boolean.TRUE);
            }
            String evidence = ViewRenderHelpers.trimString(row.get("evidence"));
            if (StringUtils.hasText(evidence)) {
                row.put("evidence", evidence);
                row.put("hasEvidence", Boolean.TRUE);
            }
            items.add(row);
        }
        artifact.put("items", items);
        artifact.put("itemCount", String.valueOf(items.size()));
        if (!items.isEmpty()) {
            artifact.put("hasItems", Boolean.TRUE);
        }

        List<?> uncertainties = (List<?>) artifact.get("uncertainties");
        if (!uncertainties.isEmpty()) {
            artifact.put("hasUncertainties", Boolean.TRUE);
            artifact.put("uncertaintyCount", String.valueOf(uncertainties.size()));
        }

        String source = ViewRenderHelpers.trimString(artifact.get("source")).toLowerCase(Locale.ROOT);
        if ("paste".equals(source)) {
            artifact.put("sourceLabel", "粘贴列表");
            artifact.put("sourceClass", "gray");
        } else if ("ph".equals(source)) {
            artifact.put("sourceLabel", "Product Hunt");
            artifact.put("sourceClass", "info");
        } else {
            artifact.put("sourceLabel", "列表");
            artifact.put("sourceClass", "gray");
        }

        Object deepFetchRaw = artifact.get("deepFetch");
        if (deepFetchRaw instanceof Number) {
            artifact.put("deepFetchLabel", String.valueOf(((Number) deepFetchRaw).intValue()));
        } else {
            String deepFetchText = ViewRenderHelpers.trimString(deepFetchRaw);
            artifact.put("deepFetchLabel", StringUtils.hasText(deepFetchText) ? deepFetchText : "0");
        }
        return artifact;
    }
}

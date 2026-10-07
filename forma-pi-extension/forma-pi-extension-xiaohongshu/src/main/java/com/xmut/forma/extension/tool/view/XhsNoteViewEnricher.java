package com.xmut.forma.extension.tool.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * xhs-note view enricher（场景模块自注册）。
 */
public final class XhsNoteViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "xhs-note";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.putNumberedLines(artifact, "titleOptions", "titleOptionsNumbered");
        ViewRenderHelpers.putNumberedLines(artifact, "imageHints", "imageHintsNumbered");
        Object tags = artifact.get("tags");
        if (tags instanceof List) {
            List<?> list = (List<?>) tags;
            List<String> parts = new ArrayList<String>(list.size());
            List<String> tagItems = new ArrayList<String>(list.size());
            for (Object entry : list) {
                String trimmed = ViewRenderHelpers.trimString(entry);
                if (!StringUtils.hasText(trimmed)) {
                    continue;
                }
                String bare = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
                if (!StringUtils.hasText(bare)) {
                    continue;
                }
                parts.add(bare);
                tagItems.add(bare);
            }
            if (!parts.isEmpty()) {
                artifact.put("tagsDisplay", ViewRenderHelpers.joinWithMiddleDot(parts));
                artifact.put("tagItems", tagItems);
                artifact.put("hasTags", Boolean.TRUE);
            }
        }
        String body = ViewRenderHelpers.trimString(artifact.get("body"));
        if (StringUtils.hasText(body)) {
            artifact.put("bodyParagraphs", ViewRenderHelpers.splitParagraphs(body));
        }
        Object imageHints = artifact.get("imageHints");
        if (imageHints instanceof List && !((List<?>) imageHints).isEmpty()) {
            artifact.put("hasImageHints", Boolean.TRUE);
        }
        Object titleOptions = artifact.get("titleOptions");
        if (titleOptions instanceof List && !((List<?>) titleOptions).isEmpty()) {
            artifact.put("hasTitleOptions", Boolean.TRUE);
        }
        return artifact;
    }
}

package com.xmut.forma.extension.tool.view;

import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * xhs-break view enricher（场景模块自注册）。
 */
public final class XhsBreakViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "xhs-break";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        String handoff = ViewRenderHelpers.buildXhsBreakNoteHandoffText(
                ViewRenderHelpers.trimString(artifact.get("targetProduct")),
                ViewRenderHelpers.trimString(artifact.get("structure")),
                ViewRenderHelpers.trimString(artifact.get("skeleton")),
                ViewRenderHelpers.trimString(artifact.get("rewrite")));
        if (handoff != null) {
            artifact.put("handoffPrompt", handoff);
        }
        String rewrite = ViewRenderHelpers.trimString(artifact.get("rewrite"));
        if (StringUtils.hasText(rewrite)) {
            artifact.put("rewriteParagraphs", ViewRenderHelpers.splitParagraphs(rewrite));
        }
        return artifact;
    }
}

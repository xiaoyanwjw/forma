package com.xmut.forma.extension.tool.view;

import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * ecommerce-skulist view enricher（场景模块自注册）。
 */
public final class EcommerceSkulistViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "ecommerce-skulist";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        String detailBody = ViewRenderHelpers.trimString(artifact.get("detailBody"));
        if (StringUtils.hasText(detailBody)) {
            List<String> paragraphs = ViewRenderHelpers.splitParagraphs(detailBody);
            artifact.put("detailParagraphs", paragraphs);
            if (!paragraphs.isEmpty()) {
                artifact.put("hasDetailParagraphs", Boolean.TRUE);
            }
        }
        ViewRenderHelpers.zipFramePrompts(artifact);
        Object frameEntries = artifact.get("frameEntries");
        if (frameEntries instanceof List && !((List<?>) frameEntries).isEmpty()) {
            artifact.put("frameCount", Integer.valueOf(((List<?>) frameEntries).size()));
            artifact.put("hasFrames", Boolean.TRUE);
        }
        Object modules = artifact.get("modules");
        if (modules instanceof List && !((List<?>) modules).isEmpty()) {
            artifact.put("hasModules", Boolean.TRUE);
        }
        return artifact;
    }
}

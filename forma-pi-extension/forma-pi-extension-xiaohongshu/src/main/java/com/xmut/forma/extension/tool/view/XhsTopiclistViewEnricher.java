package com.xmut.forma.extension.tool.view;

import java.util.Map;

/**
 * xhs-topiclist view enricher（场景模块自注册）。
 */
public final class XhsTopiclistViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "xhs-topiclist";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.enrichItems(artifact, ViewRenderHelpers::topicItemHelpers);
        ViewRenderHelpers.putItemCount(artifact);
        return artifact;
    }
}

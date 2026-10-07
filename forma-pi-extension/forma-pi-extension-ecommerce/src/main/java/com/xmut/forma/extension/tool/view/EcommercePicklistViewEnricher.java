package com.xmut.forma.extension.tool.view;

import java.util.Map;

/**
 * ecommerce-picklist view enricher（场景模块自注册）。
 */
public final class EcommercePicklistViewEnricher implements ViewEnricher {

    @Override
    public String skillId() {
        return "ecommerce-picklist";
    }

    @Override
    public Map<String, Object> enrich(Map<String, Object> artifact) {
        return doEnrich(artifact);
    }

    private static Map<String, Object> doEnrich(Map<String, Object> artifact) {
        ViewRenderHelpers.enrichItems(artifact, ViewRenderHelpers::pickItemHelpers);
        ViewRenderHelpers.putItemCount(artifact);
        return artifact;
    }
}

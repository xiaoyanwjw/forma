package com.xmut.ebus.extension.tool.view;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * In-memory display fields for skill templates.
 * Identity for every skill until handoff helpers are filled in.
 */
public final class ViewRenderHelpers {

    private ViewRenderHelpers() {
    }

    public static Map<String, Object> enrich(String skillId, Map<String, Object> artifact) {
        Map<String, Object> source = artifact == null
                ? new LinkedHashMap<String, Object>()
                : artifact;
        if (!StringUtils.hasText(skillId)) {
            return source;
        }
        return source;
    }
}

package com.xmut.forma.common.output;

import java.util.Collections;
import java.util.Map;

/**
 * Dual-track parse of a model final response: optional Skill view + business payload for persistence.
 */
public final class ParsedGenerationOutput {

    private final Map<String, Object> rawView;
    private final Map<String, Object> businessPayload;

    public ParsedGenerationOutput(Map<String, Object> rawView, Map<String, Object> businessPayload) {
        this.rawView = rawView == null ? null : Collections.unmodifiableMap(rawView);
        this.businessPayload = businessPayload == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(businessPayload);
    }

    public Map<String, Object> getRawView() {
        return rawView;
    }

    public Map<String, Object> getBusinessPayload() {
        return businessPayload;
    }
}

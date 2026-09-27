package com.xmut.ebus.application.business.agent.support;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a billing persist plugin: opaque artifact + optional Skill view + SSE extras.
 */
public final class PersistedGenerationArtifact {

    private final String artifactRef;
    private final Object artifact;
    private final Map<String, Object> rawView;
    private final Map<String, Object> readyExtras;

    public PersistedGenerationArtifact(String artifactRef,
                                       Object artifact,
                                       Map<String, Object> rawView,
                                       Map<String, Object> readyExtras) {
        this.artifactRef = artifactRef;
        this.artifact = artifact;
        this.rawView = rawView == null ? null : Collections.unmodifiableMap(rawView);
        this.readyExtras = readyExtras == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(readyExtras);
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public Object getArtifact() {
        return artifact;
    }

    public Map<String, Object> getRawView() {
        return rawView;
    }

    public Map<String, Object> getReadyExtras() {
        return readyExtras;
    }
}

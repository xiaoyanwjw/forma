package com.xmut.ebus.application.business.agent.support;

import java.util.Collections;
import java.util.Map;

/**
 * Result of artifact persist: opaque ref + optional SSE extras (no embedded business items).
 */
public final class PersistedGenerationArtifact {

    private final String artifactRef;
    private final Map<String, Object> readyExtras;
    private final Object artifact;
    private final Map<String, Object> rawView;

    public PersistedGenerationArtifact(String artifactRef, Map<String, Object> readyExtras) {
        this.artifactRef = artifactRef;
        this.readyExtras = readyExtras == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(readyExtras);
        this.artifact = null;
        this.rawView = null;
    }

    /**
     * Task 3 bridge: legacy picklist persist still supplies view context for {@link ComputerViewResolver}.
     */
    PersistedGenerationArtifact(String artifactRef,
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

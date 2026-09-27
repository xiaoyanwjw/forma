package com.xmut.ebus.application.business.agent.support;

import java.util.Collections;
import java.util.Map;

/**
 * Result of artifact persist: opaque ref + optional SSE extras (no embedded business items).
 */
public final class PersistedGenerationArtifact {

    private final String artifactRef;
    private final Map<String, Object> readyExtras;

    public PersistedGenerationArtifact(String artifactRef, Map<String, Object> readyExtras) {
        this.artifactRef = artifactRef;
        this.readyExtras = readyExtras == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(readyExtras);
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public Map<String, Object> getReadyExtras() {
        return readyExtras;
    }
}

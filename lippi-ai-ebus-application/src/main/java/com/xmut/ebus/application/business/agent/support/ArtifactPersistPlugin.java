package com.xmut.ebus.application.business.agent.support;

/**
 * Billing persist SPI keyed by {@link SkillRunProfile#getPersistAs()}.
 */
public interface ArtifactPersistPlugin {

    String persistAs();

    /**
     * Parse model final text, persist usable artifact, return handle for view + SSE.
     */
    PersistedGenerationArtifact persist(String userId, String runId, String sceneCode, String finalResponse);
}

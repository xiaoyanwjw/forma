package com.xmut.ebus.domain.business.agent.repository;

import com.xmut.ebus.domain.business.agent.model.GenerationRun;

import java.time.Instant;
import java.util.Optional;

/**
 * GenerationRun 持久化端口（AgentRuntime 真相）。
 */
public interface GenerationRunRepository {

    void save(GenerationRun run);

    void update(GenerationRun run);

    Optional<GenerationRun> findById(String id);

    /**
     * 该会话下本人最近一次带成果引用、且成果类型为 picklist/sku、
     * artifact.created_at &gt;= since 的 usable artifact_ref。
     */
    Optional<String> findLatestSettledArtifactRefBySession(String userId, String sessionId, Instant since);

    /**
     * 同上，并限定 {@code artifact_type}（如 {@code picklist} / {@code sku}）。
     */
    Optional<String> findLatestSettledArtifactRefBySession(String userId,
                                                           String sessionId,
                                                           Instant since,
                                                           String artifactType);
}

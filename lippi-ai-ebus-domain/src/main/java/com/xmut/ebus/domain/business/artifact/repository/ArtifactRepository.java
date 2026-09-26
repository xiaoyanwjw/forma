package com.xmut.ebus.domain.business.artifact.repository;

import com.xmut.ebus.domain.business.artifact.model.Artifact;

import java.util.Optional;

/**
 * 通用成果仓储（物理表 {@code ebus_artifact}）。
 */
public interface ArtifactRepository {

    void save(Artifact artifact);

    Optional<Artifact> findById(String id);

    Optional<Artifact> findByRunId(String runId);
}

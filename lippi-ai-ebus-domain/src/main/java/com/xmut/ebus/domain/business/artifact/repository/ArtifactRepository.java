package com.xmut.ebus.domain.business.artifact.repository;

import com.xmut.ebus.domain.business.artifact.model.Artifact;

import java.util.Optional;

/**
 * 通用成果仓储（物理表 {@code ebus_artifact}）。
 */
public interface ArtifactRepository {

    void save(Artifact artifact);

    /** 按 biz_id 覆盖写（同 Run 策划→执行）。 */
    void update(Artifact artifact);

    Optional<Artifact> findById(String id);

    Optional<Artifact> findByRunId(String runId);
}

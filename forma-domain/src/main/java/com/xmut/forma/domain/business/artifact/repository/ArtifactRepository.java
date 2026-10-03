package com.xmut.forma.domain.business.artifact.repository;

import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 通用成果仓储（物理表 {@code forma_artifact}）。
 */
public interface ArtifactRepository {

    void save(Artifact artifact);

    /** 按 biz_id 覆盖写（同 Run 策划→执行）。 */
    void update(Artifact artifact);

    Optional<Artifact> findById(String id);

    Optional<Artifact> findByRunId(String runId);

    /**
     * 本人历史列表：时间窗 + 类型集合 + 可选场景；新在前。
     */
    List<Artifact> listByUserSince(String userId,
                                   Instant sinceInclusive,
                                   Collection<ArtifactType> types,
                                   String sceneCodeOrNull);
}

package com.xmut.ebus.infrastructure.persistence.repository.business.artifact;

import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.ArtifactMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.ArtifactPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ArtifactRepositoryImpl implements ArtifactRepository {

    private final ArtifactMapper artifactMapper;

    @Override
    public void save(Artifact artifact) {
        artifactMapper.insert(toPo(artifact));
    }

    @Override
    public void update(Artifact artifact) {
        artifactMapper.updateByBizId(toPo(artifact));
    }

    @Override
    public Optional<Artifact> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        ArtifactPO po = artifactMapper.selectByBizId(id.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public Optional<Artifact> findByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Optional.empty();
        }
        ArtifactPO po = artifactMapper.selectByRunId(runId.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    private static Artifact toDomain(ArtifactPO po) {
        Artifact artifact = new Artifact();
        artifact.setId(po.getBizId());
        artifact.setUserId(po.getUserId());
        artifact.setRunId(po.getRunId());
        artifact.setType(ArtifactType.fromCode(po.getArtifactType()));
        artifact.setSceneCode(po.getSceneCode());
        artifact.setTemplateId(po.getTemplateId());
        artifact.setTitle(po.getTitle());
        artifact.setPayloadJson(po.getPayloadJson());
        artifact.setCreatedAt(po.getCreatedAt());
        artifact.setUpdatedAt(po.getUpdatedAt());
        return artifact;
    }

    private static ArtifactPO toPo(Artifact artifact) {
        ArtifactPO po = new ArtifactPO();
        po.setBizId(artifact.getId());
        po.setUserId(artifact.getUserId());
        po.setRunId(artifact.getRunId());
        po.setArtifactType(artifact.getType() == null ? null : artifact.getType().getCode());
        po.setSceneCode(artifact.getSceneCode());
        po.setTemplateId(artifact.getTemplateId());
        po.setTitle(artifact.getTitle());
        po.setPayloadJson(artifact.getPayloadJson());
        po.setCreatedAt(artifact.getCreatedAt());
        po.setUpdatedAt(artifact.getUpdatedAt());
        return po;
    }
}

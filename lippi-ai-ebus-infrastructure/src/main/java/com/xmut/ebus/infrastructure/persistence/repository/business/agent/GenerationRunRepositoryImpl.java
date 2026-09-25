package com.xmut.ebus.infrastructure.persistence.repository.business.agent;

import com.xmut.ebus.domain.business.agent.constant.GenerationRunStatus;
import com.xmut.ebus.domain.business.agent.model.GenerationRun;
import com.xmut.ebus.domain.business.agent.repository.GenerationRunRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.GenerationRunMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.GenerationRunPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GenerationRunRepositoryImpl implements GenerationRunRepository {

    private final GenerationRunMapper generationRunMapper;

    @Override
    public void save(GenerationRun run) {
        generationRunMapper.insert(toPo(run));
    }

    @Override
    public void update(GenerationRun run) {
        generationRunMapper.update(toPo(run));
    }

    @Override
    public Optional<GenerationRun> findById(String id) {
        return Optional.ofNullable(generationRunMapper.selectById(id)).map(this::toDomain);
    }

    private GenerationRunPO toPo(GenerationRun run) {
        GenerationRunPO po = new GenerationRunPO();
        po.setId(run.getId());
        po.setUserId(run.getUserId());
        po.setHoldId(run.getHoldId());
        po.setSessionId(run.getSessionId());
        po.setArtifactRef(run.getArtifactRef());
        po.setStatus(run.getStatus().name());
        po.setCreatedAt(run.getCreatedAt());
        po.setUpdatedAt(run.getUpdatedAt());
        return po;
    }

    private GenerationRun toDomain(GenerationRunPO po) {
        GenerationRun run = new GenerationRun();
        run.setId(po.getId());
        run.setUserId(po.getUserId());
        run.setHoldId(po.getHoldId());
        run.setSessionId(po.getSessionId());
        run.setArtifactRef(po.getArtifactRef());
        run.setStatus(GenerationRunStatus.fromCode(po.getStatus()));
        run.setCreatedAt(po.getCreatedAt());
        run.setUpdatedAt(po.getUpdatedAt());
        return run;
    }
}

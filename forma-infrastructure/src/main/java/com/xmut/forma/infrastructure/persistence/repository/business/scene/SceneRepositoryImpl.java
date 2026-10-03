package com.xmut.forma.infrastructure.persistence.repository.business.scene;

import com.xmut.forma.domain.business.scene.constant.SceneStatus;
import com.xmut.forma.domain.business.scene.model.Scene;
import com.xmut.forma.domain.business.scene.repository.SceneRepository;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.SceneMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.ScenePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SceneRepositoryImpl implements SceneRepository {

    private final SceneMapper sceneMapper;

    @Override
    public List<Scene> listOrdered() {
        List<ScenePO> rows = sceneMapper.selectAllOrdered();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Scene> scenes = new ArrayList<Scene>(rows.size());
        for (ScenePO po : rows) {
            scenes.add(toDomain(po));
        }
        return scenes;
    }

    @Override
    public Optional<Scene> findByBizId(String bizId) {
        if (!StringUtils.hasText(bizId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sceneMapper.selectByBizId(bizId.trim())).map(this::toDomain);
    }

    @Override
    public Optional<Scene> findBySceneCode(String sceneCode) {
        if (!StringUtils.hasText(sceneCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sceneMapper.selectBySceneCode(sceneCode.trim())).map(this::toDomain);
    }

    private Scene toDomain(ScenePO po) {
        Scene scene = new Scene();
        scene.setId(po.getBizId());
        scene.setSceneCode(po.getSceneCode());
        scene.setDisplayName(po.getDisplayName());
        scene.setStatus(SceneStatus.fromCode(po.getStatus()));
        scene.setSortOrder(po.getSortOrder() == null ? 0 : po.getSortOrder());
        scene.setSummary(po.getSummary());
        scene.setCreatedAt(po.getCreatedAt());
        scene.setUpdatedAt(po.getUpdatedAt());
        return scene;
    }
}

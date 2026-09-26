package com.xmut.ebus.application.business.scene.query;

import com.xmut.ebus.application.business.scene.dto.SceneDTO;
import com.xmut.ebus.domain.business.scene.model.Scene;
import com.xmut.ebus.domain.business.scene.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SceneCatalog 只读用例（画廊列表）。
 */
@Service
@RequiredArgsConstructor
public class SceneQueryService {

    private final SceneRepository sceneRepository;

    @Transactional(readOnly = true)
    public List<SceneDTO> list() {
        List<Scene> scenes = sceneRepository.listOrdered();
        List<SceneDTO> result = new ArrayList<SceneDTO>(scenes.size());
        for (Scene scene : scenes) {
            result.add(toDto(scene));
        }
        return result;
    }

    private static SceneDTO toDto(Scene scene) {
        return new SceneDTO(
                scene.getId(),
                scene.getSceneCode(),
                scene.getDisplayName(),
                scene.getStatus().name(),
                scene.getSortOrder(),
                scene.getSummary());
    }
}

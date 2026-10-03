package com.xmut.forma.application.business.scene.query;

import com.xmut.forma.application.business.scene.dto.SceneDTO;
import com.xmut.forma.application.business.scene.dto.SceneSkillCapsuleDTO;
import com.xmut.forma.application.business.scene.dto.SceneSkillCapsuleItemDTO;
import com.xmut.forma.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.application.business.scene.pack.SceneSkillCapsuleLoader;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.scene.model.Scene;
import com.xmut.forma.domain.business.scene.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SceneCatalog 只读用例（画廊列表 + 工作台快捷栏）。
 */
@Service
@RequiredArgsConstructor
public class SceneQueryService {

    private final SceneRepository sceneRepository;
    private final SceneCapabilityPackLoader packLoader;
    private final SceneSkillCapsuleLoader capsuleLoader;

    @Transactional(readOnly = true)
    public List<SceneDTO> list() {
        List<Scene> scenes = sceneRepository.listOrdered();
        List<SceneDTO> result = new ArrayList<SceneDTO>(scenes.size());
        for (Scene scene : scenes) {
            result.add(toDto(scene));
        }
        return result;
    }

    /**
     * 工作台胶囊栏：有 {@code launch.json} 的 skill；无包/缺文件时返回空列表。
     */
    public SceneSkillCapsuleDTO listSkillCapsules(String sceneCode) {
        String code = StringUtils.requireHasText(sceneCode, "sceneCode 不能为空").trim();
        try {
            SceneCapabilityPack pack = packLoader.load(code);
            List<SceneSkillCapsuleItemDTO> skills = capsuleLoader.loadFor(pack.getSkills());
            return new SceneSkillCapsuleDTO(code, skills);
        } catch (BusinessException ex) {
            return new SceneSkillCapsuleDTO(code, Collections.<SceneSkillCapsuleItemDTO>emptyList());
        }
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

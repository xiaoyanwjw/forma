package com.xmut.forma.application.business.scene.pack;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 按 sceneCode 从已注册 {@link SkillCatalog} 选型能力包，必含 Skill 以 {@link SceneMetaCatalog}
 *（{@code pack.yaml}）为准，不在 Java 里按场景写死清单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneCapabilityPackLoader {

    public static final String MSG_PACK_UNAVAILABLE = "场景能力暂不可用";

    private final SkillCatalog skillConfig;
    private final SceneMetaCatalog sceneMetaCatalog;

    /**
     * @throws BusinessException 未知码、无 pack.yaml、空 skill 清单，或缺必含 skill
     */
    public SceneCapabilityPack load(String sceneCode) {
        Objects.requireNonNull(skillConfig, "skillConfig");
        Objects.requireNonNull(sceneMetaCatalog, "sceneMetaCatalog");
        String code = StringUtils.requireHasText(sceneCode, MSG_PACK_UNAVAILABLE).trim();
        SceneMeta meta = sceneMetaCatalog.find(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE));
        List<Skill> skills = skillConfig.listByScene(code);
        if (skills == null || skills.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
        }

        SceneCapabilityPack pack = new SceneCapabilityPack(code, skills);
        validatePack(pack, meta);
        return pack;
    }

    private static void validatePack(SceneCapabilityPack pack, SceneMeta meta) {
        for (String required : meta.getRequiredSkills()) {
            if (!pack.hasSkill(required)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
            }
        }
    }
}

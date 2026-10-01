package com.xmut.ebus.application.business.scene.pack;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 按 sceneCode 从已注册 {@link SkillCatalog} 选型能力包，不二次扫描或解析 SKILL.md。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneCapabilityPackLoader {

    public static final String MSG_PACK_UNAVAILABLE = "场景能力暂不可用";

    /** 空跑默认注入的 skill（3.4/3.6 再按意图选）。 */
    public static final String DEFAULT_EMPTY_RUN_SKILL_ID = "ecommerce-picklist";

    public static final String SKILL_PICKLIST = "ecommerce-picklist";
    public static final String SKILL_SKULIST = "ecommerce-skulist";
    public static final String SKILL_XHS_TOPICLIST = "xhs-topiclist";
    public static final String SKILL_XHS_NOTE = "xhs-note";
    public static final String SKILL_XHS_BREAK = "xhs-break";

    static final String SCENE_ECOMMERCE = "ecommerce";
    static final String SCENE_XHS = "xiaohongshu";

    private static final Set<String> ECOMMERCE_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    SKILL_PICKLIST, SKILL_SKULIST)));
    private static final Set<String> XHS_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    SKILL_XHS_TOPICLIST, SKILL_XHS_NOTE, SKILL_XHS_BREAK)));

    private final SkillCatalog skillConfig;

    /**
     * @throws BusinessException 未知码、空清单，或电商/小红书包缺必含 skill
     */
    public SceneCapabilityPack load(String sceneCode) {
        Objects.requireNonNull(skillConfig, "skillConfig");
        String code = StringUtils.requireHasText(sceneCode, MSG_PACK_UNAVAILABLE).trim();
        List<Skill> skills = skillConfig.listByScene(code);
        if (skills == null || skills.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
        }

        SceneCapabilityPack pack = new SceneCapabilityPack(code, skills);
        validatePack(pack);
        return pack;
    }

    private static void validatePack(SceneCapabilityPack pack) {
        Set<String> requiredSkills = requiredSkillsFor(pack.getSceneCode());
        if (requiredSkills == null) {
            return;
        }
        for (String required : requiredSkills) {
            if (!pack.hasSkill(required)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
            }
        }
    }

    private static Set<String> requiredSkillsFor(String sceneCode) {
        if (SCENE_ECOMMERCE.equals(sceneCode)) {
            return ECOMMERCE_REQUIRED_SKILLS;
        }
        if (SCENE_XHS.equals(sceneCode)) {
            return XHS_REQUIRED_SKILLS;
        }
        return null;
    }
}

package com.xmut.forma.application.business.scene.pack;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
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

    public static final String SKILL_PICKLIST = "ecommerce-picklist";
    public static final String SKILL_SKULIST = "ecommerce-skulist";
    public static final String SKILL_XHS_TOPICLIST = "xhs-topiclist";
    public static final String SKILL_XHS_NOTE = "xhs-note";
    public static final String SKILL_XHS_BREAK = "xhs-break";
    public static final String SKILL_TECH_DIGEST = "tech-digest";

    static final String SCENE_ECOMMERCE = "ecommerce";
    static final String SCENE_XHS = "xiaohongshu";
    static final String SCENE_TECH_DIGEST = "tech_digest";

    private static final Set<String> ECOMMERCE_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    SKILL_PICKLIST, SKILL_SKULIST)));
    private static final Set<String> XHS_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    SKILL_XHS_TOPICLIST, SKILL_XHS_NOTE, SKILL_XHS_BREAK)));
    private static final Set<String> TECH_DIGEST_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Collections.singletonList(
                    SKILL_TECH_DIGEST)));

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
        if (SCENE_TECH_DIGEST.equals(sceneCode)) {
            return TECH_DIGEST_REQUIRED_SKILLS;
        }
        return null;
    }
}

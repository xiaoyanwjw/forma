package com.xmut.forma.pi.agent.skill;

import com.xmut.forma.pi.agent.TurnInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 本轮 Skill 选择器。
 * 功能描述：按 skillId 优先于 domain 做显式选择。
 * 关键设计：未入册时 fail-closed，不回落全量 tools。
 */
public final class SkillSelector {

    private static final Logger log = LoggerFactory.getLogger(SkillSelector.class);

    private SkillSelector() {}

    public static ActiveSkill select(TurnInput turn, SkillCatalog skillConfig) {
        String skillId = skillIdOf(turn);
        if (!StringUtils.hasText(skillId)) {
            return ActiveSkill.NONE;
        }
        if (skillConfig == null) {
            throw new IllegalArgumentException("unknown skillId: " + skillId
                    + " (SkillCatalog unavailable)");
        }
        return skillConfig.resolve(skillId)
                .map(ActiveSkill::of)
                .orElseThrow(() -> {
                    log.debug("SkillSelector: skill not found id={}", skillId);
                    return new IllegalArgumentException("unknown skillId: " + skillId);
                });
    }

    /** {@code skillId} 优先于 {@code domain}。 */
    public static String skillIdOf(TurnInput turn) {
        if (turn == null) {
            return null;
        }
        if (StringUtils.hasText(turn.getSkillId())) {
            return turn.getSkillId().trim();
        }
        if (StringUtils.hasText(turn.getDomain())) {
            return turn.getDomain().trim();
        }
        return null;
    }
}

package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;

/**
 * Per-run policy for the generic Generation pipeline (not Computer protocol).
 * persistAs 来自 SKILL.md {@code metadata.persistAs}，不按场景写死。
 */
public final class SkillRunProfile {

    /** 无 Skill / dry：不按目录落业务成果。 */
    public static final String PERSIST_NONE = "none";

    private final String skillId;
    private final boolean settleEnabled;
    private final String persistAs;
    private final boolean requireUserText;
    private final boolean dryRun;
    private final boolean skillBound;

    private SkillRunProfile(String skillId,
                            boolean settleEnabled,
                            String persistAs,
                            boolean requireUserText,
                            boolean dryRun,
                            boolean skillBound) {
        this.skillId = skillId;
        this.settleEnabled = settleEnabled;
        this.persistAs = persistAs;
        this.requireUserText = requireUserText;
        this.dryRun = dryRun;
        this.skillBound = skillBound;
    }

    /** Probe / empty-run: bind a skill, never settle, end as run_failed. */
    public static SkillRunProfile dry(String skillId) {
        String id = StringUtils.hasText(skillId) ? skillId.trim() : SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID;
        return new SkillRunProfile(id, false, PERSIST_NONE, false, true, true);
    }

    /** Chat / draft without Skill: markdown Computer view; settle after usable view. */
    public static SkillRunProfile noSkill() {
        return new SkillRunProfile(null, true, PERSIST_NONE, true, false, false);
    }

    /**
     * 已绑定计费 Skill（persistAs 须与 SKILL.md 一致；单测/夹具用）。
     */
    public static SkillRunProfile billed(String skillId, String persistAs) {
        if (!StringUtils.hasText(skillId) || !StringUtils.hasText(persistAs)
                || PERSIST_NONE.equalsIgnoreCase(persistAs.trim())) {
            throw new IllegalArgumentException("billed profile needs skillId and persistAs from SKILL.md");
        }
        return new SkillRunProfile(skillId.trim(), true, persistAs.trim(), true, false, true);
    }

    /**
     * Resolve profile from API flags + Skill 目录（persistAs 来自 SKILL.md）。
     * <ul>
     *   <li>{@code dryRun} → dry probe</li>
     *   <li>blank {@code skillId} → no-skill markdown path</li>
     *   <li>目录中有 {@code persistAs} 的 Skill → settle 路径</li>
     * </ul>
     */
    public static SkillRunProfile resolve(String skillId, boolean dryRun) {
        return resolve(null, skillId, dryRun);
    }

    public static SkillRunProfile resolve(SkillCatalog skillCatalog, String skillId, boolean dryRun) {
        if (dryRun) {
            return dry(skillId);
        }
        if (!StringUtils.hasText(skillId)) {
            return noSkill();
        }
        String id = skillId.trim();
        if (skillCatalog == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "暂不支持该 Skill 计费生成: " + id);
        }
        Skill skill = skillCatalog.resolve(id).orElse(null);
        if (skill == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "暂不支持该 Skill 计费生成: " + id);
        }
        String persistAs = skill.getPersistAs();
        if (!StringUtils.hasText(persistAs) || PERSIST_NONE.equalsIgnoreCase(persistAs.trim())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该 Skill 未配置成果落库类型: " + id);
        }
        return billed(skill.getId(), persistAs);
    }

    public String getSkillId() {
        return skillId;
    }

    public boolean isSettleEnabled() {
        return settleEnabled;
    }

    public String getPersistAs() {
        return persistAs;
    }

    public boolean isRequireUserText() {
        return requireUserText;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isSkillBound() {
        return skillBound;
    }

    /** 计费且 persistAs 与 SKILL.md 码一致。 */
    public boolean persistsAs(String persistAs) {
        return settleEnabled && StringUtils.hasText(persistAs) && persistAs.equals(this.persistAs);
    }
}

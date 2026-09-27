package com.xmut.ebus.application.business.agent.support;

import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;

/**
 * Per-skill run policy for the generic Generation pipeline (not Computer protocol).
 */
public final class SkillRunProfile {

    public static final String PERSIST_NONE = "none";
    public static final String PERSIST_PICKLIST = "picklist";

    private final String skillId;
    private final boolean settleEnabled;
    private final String persistAs;
    private final boolean requireUserText;
    private final boolean dryRun;

    private SkillRunProfile(String skillId,
                            boolean settleEnabled,
                            String persistAs,
                            boolean requireUserText,
                            boolean dryRun) {
        this.skillId = skillId;
        this.settleEnabled = settleEnabled;
        this.persistAs = persistAs;
        this.requireUserText = requireUserText;
        this.dryRun = dryRun;
    }

    public static SkillRunProfile dry(String skillId) {
        String id = StringUtils.hasText(skillId) ? skillId.trim() : SceneCapabilityPackLoader.DEFAULT_EMPTY_RUN_SKILL_ID;
        return new SkillRunProfile(id, false, PERSIST_NONE, false, true);
    }

    public static SkillRunProfile billedPicklist() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_PICKLIST, true, PERSIST_PICKLIST, true, false);
    }

    /**
     * Resolve profile from API flags. Unknown billed skills are rejected until a plugin exists.
     */
    public static SkillRunProfile resolve(String skillId, boolean dryRun) {
        if (dryRun) {
            return dry(skillId);
        }
        String id = StringUtils.hasText(skillId) ? skillId.trim() : SceneCapabilityPackLoader.SKILL_PICKLIST;
        if (SceneCapabilityPackLoader.SKILL_PICKLIST.equals(id)) {
            return billedPicklist();
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "暂不支持该 Skill 计费生成: " + id);
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
}

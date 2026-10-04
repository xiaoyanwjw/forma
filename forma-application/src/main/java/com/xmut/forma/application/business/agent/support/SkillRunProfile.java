package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;

/**
 * Per-run policy for the generic Generation pipeline (not Computer protocol).
 */
public final class SkillRunProfile {

    public static final String PERSIST_NONE = "none";
    public static final String PERSIST_PICKLIST = "picklist";
    public static final String PERSIST_SKU = "sku";
    public static final String PERSIST_LISTING_PLAN = "listing_plan";
    public static final String PERSIST_XHS_TOPICLIST = "xhs_topiclist";
    public static final String PERSIST_XHS_NOTE = "xhs_note";
    public static final String PERSIST_XHS_BREAK = "xhs_break";
    public static final String PERSIST_TECH_DIGEST = "tech_digest";

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

    public static SkillRunProfile billedPicklist() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_PICKLIST, true, PERSIST_PICKLIST, true, false, true);
    }

    public static SkillRunProfile billedListing() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_SKULIST, true, PERSIST_SKU, true, false, true);
    }

    public static SkillRunProfile billedXhsTopiclist() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, true, PERSIST_XHS_TOPICLIST, true, false, true);
    }

    public static SkillRunProfile billedXhsNote() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_XHS_NOTE, true, PERSIST_XHS_NOTE, true, false, true);
    }

    public static SkillRunProfile billedXhsBreak() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_XHS_BREAK, true, PERSIST_XHS_BREAK, true, false, true);
    }

    public static SkillRunProfile billedTechDigest() {
        return new SkillRunProfile(
                SceneCapabilityPackLoader.SKILL_TECH_DIGEST, true, PERSIST_TECH_DIGEST, true, false, true);
    }

    /**
     * Resolve profile from API flags.
     * <ul>
     *   <li>{@code dryRun} → dry probe</li>
     *   <li>blank {@code skillId} → no-skill markdown path</li>
     *   <li>known billed skill → settle path</li>
     * </ul>
     */
    public static SkillRunProfile resolve(String skillId, boolean dryRun) {
        if (dryRun) {
            return dry(skillId);
        }
        if (!StringUtils.hasText(skillId)) {
            return noSkill();
        }
        String id = skillId.trim();
        if (SceneCapabilityPackLoader.SKILL_PICKLIST.equals(id)) {
            return billedPicklist();
        }
        if (SceneCapabilityPackLoader.SKILL_SKULIST.equals(id)) {
            return billedListing();
        }
        if (SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST.equals(id)) {
            return billedXhsTopiclist();
        }
        if (SceneCapabilityPackLoader.SKILL_XHS_NOTE.equals(id)) {
            return billedXhsNote();
        }
        if (SceneCapabilityPackLoader.SKILL_XHS_BREAK.equals(id)) {
            return billedXhsBreak();
        }
        if (SceneCapabilityPackLoader.SKILL_TECH_DIGEST.equals(id)) {
            return billedTechDigest();
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

    public boolean isSkillBound() {
        return skillBound;
    }

    /** Billed picklist: persist picklist artifact（search_sku 为 skill 软约束，非本方法门禁）. */
    public boolean isBilledPicklist() {
        return settleEnabled && PERSIST_PICKLIST.equals(persistAs);
    }

    /** Billed Listing：persist sku；系统挂载占位主图后再 settle. */
    public boolean isBilledSku() {
        return settleEnabled && PERSIST_SKU.equals(persistAs);
    }

    public boolean isBilledXhsTopiclist() {
        return settleEnabled && PERSIST_XHS_TOPICLIST.equals(persistAs);
    }

    public boolean isBilledXhsNote() {
        return settleEnabled && PERSIST_XHS_NOTE.equals(persistAs);
    }

    public boolean isBilledXhsBreak() {
        return settleEnabled && PERSIST_XHS_BREAK.equals(persistAs);
    }

    public boolean isBilledTechDigest() {
        return settleEnabled && PERSIST_TECH_DIGEST.equals(persistAs);
    }
}

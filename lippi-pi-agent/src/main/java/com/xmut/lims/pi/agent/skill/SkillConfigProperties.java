package com.xmut.lims.pi.agent.skill;

/**
 * SkillConfig 运行时开关。
 * 功能描述：控制是否允许运行时突变（默认 false）。
 */
public final class SkillConfigProperties {

    private final boolean allowRuntimeMutation;

    public SkillConfigProperties() {
        this(false);
    }

    public SkillConfigProperties(boolean allowRuntimeMutation) {
        this.allowRuntimeMutation = allowRuntimeMutation;
    }

    public boolean isAllowRuntimeMutation() {
        return allowRuntimeMutation;
    }

    public static SkillConfigProperties defaults() {
        return new SkillConfigProperties(false);
    }

    public static SkillConfigProperties allowMutation() {
        return new SkillConfigProperties(true);
    }
}

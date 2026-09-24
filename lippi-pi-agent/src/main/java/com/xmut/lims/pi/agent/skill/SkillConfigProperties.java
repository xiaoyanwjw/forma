package com.xmut.lims.pi.agent.skill;

/**
 * SkillConfig 运行时突变开关（Story 51-9 / FR24）。
 *
 * <p>配置键：{@code pi.skills.allow-runtime-mutation}，默认 {@code false}。
 * 生产禁止 agent/模型热更新 Config；仅启动装载（bootstrap）或测试显式打开。
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

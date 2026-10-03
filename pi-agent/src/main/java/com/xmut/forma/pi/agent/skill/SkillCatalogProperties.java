package com.xmut.forma.pi.agent.skill;

/**
 * SkillCatalog 运行时开关。
 * 功能描述：控制是否允许运行时突变（默认 false）。
 */
public final class SkillCatalogProperties {

    private final boolean allowRuntimeMutation;

    public SkillCatalogProperties() {
        this(false);
    }

    public SkillCatalogProperties(boolean allowRuntimeMutation) {
        this.allowRuntimeMutation = allowRuntimeMutation;
    }

    public boolean isAllowRuntimeMutation() {
        return allowRuntimeMutation;
    }

    public static SkillCatalogProperties defaults() {
        return new SkillCatalogProperties(false);
    }

    public static SkillCatalogProperties allowMutation() {
        return new SkillCatalogProperties(true);
    }
}

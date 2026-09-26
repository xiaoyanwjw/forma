package com.xmut.lims.pi.agent.skill;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本轮已激活 Skill。
 * 功能描述：表示显式选中的 Skill；NONE 表示未指定或 resolve 失败。
 */
public final class ActiveSkill {

    public static final ActiveSkill NONE = new ActiveSkill(null);

    private final Skill skill;

    private ActiveSkill(Skill skill) {
        this.skill = skill;
    }

    public static ActiveSkill of(Skill skill) {
        return skill == null ? NONE : new ActiveSkill(skill);
    }

    public boolean isPresent() {
        return skill != null;
    }

    public Skill getSkill() {
        return skill;
    }

    public String getId() {
        return skill != null ? skill.getId() : null;
    }

    /** Stable skills 槽不再内联全文；保留兼容，恒为 null。 */
    public String text() {
        return null;
    }

    /**
     * null = 不裁剪 tools；empty = 不暴露任何 tool；非空 = 白名单。
     * 来自 {@link Skill#getAllowedTools()}。
     */
    public List<String> allowedTools() {
        if (skill == null) {
            return null;
        }
        return skill.getAllowedTools();
    }

    /** @deprecated 使用 {@link #allowedTools()} */
    @Deprecated
    public List<String> toolWhitelist() {
        return allowedTools();
    }

    /** 模型 useCase 已从 Skill 删除；保留兼容，恒为 null。 */
    public String modelUseCase() {
        return null;
    }

    public List<Skill> asList() {
        return skill == null
                ? Collections.emptyList()
                : Collections.singletonList(skill);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ActiveSkill)) {
            return false;
        }
        ActiveSkill that = (ActiveSkill) o;
        return Objects.equals(skill, that.skill);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(skill);
    }
}

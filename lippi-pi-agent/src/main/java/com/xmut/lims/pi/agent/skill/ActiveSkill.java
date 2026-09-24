package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.agent.TurnBinder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本轮已激活的 Skill（EXPLICIT）；NONE = 未指定 / resolve 失败。
 *
 * <p>选择发生在 {@link SkillSelector}；{@link TurnBinder} 只投影。
 */
public final class ActiveSkill {

    public static final ActiveSkill NONE = new ActiveSkill(null);

    private final SkillManifest manifest;

    private ActiveSkill(SkillManifest manifest) {
        this.manifest = manifest;
    }

    public static ActiveSkill of(SkillManifest manifest) {
        return manifest == null ? NONE : new ActiveSkill(manifest);
    }

    public boolean isPresent() {
        return manifest != null;
    }

    public SkillManifest getManifest() {
        return manifest;
    }

    public String getId() {
        return manifest != null ? manifest.getId() : null;
    }

    /** Active 的图拓扑；NONE → null。 */
    public SkillGraphTopology graphTopology() {
        return manifest != null ? manifest.getGraphTopology() : null;
    }

    /** Active 的模型 useCase；NONE / 空白 → null。 */
    public String modelUseCase() {
        if (manifest == null) {
            return null;
        }
        String uc = manifest.getModelUseCase();
        return uc != null && !uc.trim().isEmpty() ? uc.trim() : null;
    }

    /** Stable skills 槽曾用内联 prompt；现改目录文本，全文经 read_skill。保留兼容。 */
    public String text() {
        if (manifest == null) {
            return null;
        }
        String prompt = manifest.getSkillsPrompt();
        return prompt != null && !prompt.trim().isEmpty() ? prompt : null;
    }

    /**
     * null = 不裁剪 tools；empty = 不暴露任何 tool；非空 = 白名单。
     */
    public List<String> toolWhitelist() {
        if (manifest == null) {
            return null;
        }
        return manifest.getToolWhitelist();
    }

    public List<SkillManifest> asList() {
        return manifest == null
                ? Collections.emptyList()
                : Collections.singletonList(manifest);
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
        return Objects.equals(manifest, that.manifest);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(manifest);
    }
}

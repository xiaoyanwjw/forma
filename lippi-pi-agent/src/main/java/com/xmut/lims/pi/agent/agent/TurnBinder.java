package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.skill.ActiveSkill;
import com.xmut.lims.pi.agent.skill.SkillCatalogPrompt;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 极薄投影：{@link ToolConfig} + {@link SkillConfig} + {@link ActiveSkill} → {@link TurnBindings}。
 *
 * <p>不做 resolve（{@link com.xmut.lims.pi.agent.skill.SkillSelector}）；
 * 不做 classpath 扫描（Skill/Tool Bootstrap）。
 *
 * <pre>
 *   skillsText      ↔  toolsText
 *   availableSkills ↔  availableTools
 * </pre>
 *
 * <p>{@code availableSkills}：有 Active → 当前 skill；无 Active → {@code skillConfig.manifests()} 目录。
 * {@code skillsText}：目录摘要 + {@code read_skill} 指引（不是 Skill 全文；全文经 tool）。
 */
public final class TurnBinder {

    private static final Logger log = LoggerFactory.getLogger(TurnBinder.class);

    private TurnBinder() {}

    /**
     * 纯投影。{@code activeSkill == null} 视为 {@link ActiveSkill#NONE}。
     */
    public static TurnBindings bind(ToolConfig toolConfig,
                                    SkillConfig skillConfig,
                                    ActiveSkill activeSkill) {
        ToolConfig tools = toolConfig != null ? toolConfig : DefaultToolConfig.empty();
        ActiveSkill skill = activeSkill != null ? activeSkill : ActiveSkill.NONE;

        List<String> whitelist = skill.toolWhitelist();
        List<ToolSchema> availableTools = tools.schemasForModel(whitelist);
        String toolsText = tools.textForModel(whitelist);

        final List<SkillManifest> availableSkills = manifestForConfig(skillConfig, skill);
        final String skillText = SkillCatalogPrompt.build(availableSkills, skill.getId());
        if (skill.isPresent() && !StringUtils.hasText(skillText)) {
            SkillManifest m = skill.getManifest();
            log.debug("TurnBinder: active skill id={} produced empty catalog text (promptRef={})",
                    skill.getId(), m != null ? m.getPromptRef() : null);
        }

        return TurnBindings.builder()
                .availableTools(availableTools)
                .toolsText(toolsText)
                .availableSkills(availableSkills)
                .skillsText(skillText)
                .activeSkillId(skill.getId())
                .modelUseCase(skill.modelUseCase())
                .build();
    }

    static List<SkillManifest> manifestForConfig(SkillConfig skillConfig, ActiveSkill skill) {
        if (skill != null && skill.isPresent()) {
            return skill.asList();
        }
        if (skillConfig == null) {
            return Collections.emptyList();
        }

        List<SkillManifest> manifests = skillConfig.manifests();
        return manifests != null ? manifests : Collections.emptyList();
    }

    /** 无 SkillConfig 时仅投影 Active（测试便捷）。 */
    public static TurnBindings bind(ToolConfig toolConfig, ActiveSkill activeSkill) {
        return bind(toolConfig, null, activeSkill);
    }
}

package com.xmut.forma.pi.agent.agent;

import com.xmut.forma.pi.ai.model.ToolSchema;
import com.xmut.forma.pi.agent.skill.ActiveSkill;
import com.xmut.forma.pi.agent.skill.SkillCatalogPrompt;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.tool.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本轮入图投影器。
 * 功能描述：把 ToolCatalog + SkillCatalog + ActiveSkill 投影为 TurnBindings。
 */
public final class TurnBinder {

    private static final Logger log = LoggerFactory.getLogger(TurnBinder.class);

    private TurnBinder() {}

    /**
     * 纯投影。{@code toolConfig}/{@code activeSkill} 必填；
     * {@code skillConfig} 可为 null（无 Skill 目录，与 {@link DefaultAgent} 一致）。
     */
    public static TurnBindings bind(ToolCatalog toolConfig,
                                    SkillCatalog skillConfig,
                                    ActiveSkill activeSkill) {
        Objects.requireNonNull(toolConfig, "toolConfig");
        Objects.requireNonNull(activeSkill, "activeSkill");

        List<String> whitelist = activeSkill.allowedTools();
        List<ToolSchema> availableTools = toolConfig.schemasForModel(whitelist);
        String toolsText = toolConfig.textForModel(whitelist);

        final List<Skill> availableSkills = manifestForConfig(skillConfig, activeSkill);
        final String skillText = SkillCatalogPrompt.build(availableSkills, activeSkill.getId());
        if (activeSkill.isPresent() && !StringUtils.hasText(skillText)) {
            Skill m = activeSkill.getSkill();
            log.debug("TurnBinder: active skill id={} produced empty catalog text (promptRef={})",
                    activeSkill.getId(), m != null ? m.getPromptRef() : null);
        }

        return TurnBindings.builder()
                .availableTools(availableTools)
                .toolsText(toolsText)
                .availableSkills(availableSkills)
                .skillsText(skillText)
                .activeSkillId(activeSkill.getId())
                .modelUseCase(activeSkill.modelUseCase())
                .activeTools(whitelist)
                .build();
    }

    static List<Skill> manifestForConfig(SkillCatalog skillConfig, ActiveSkill skill) {
        if (skill != null && skill.isPresent()) {
            return skill.asList();
        }
        if (skillConfig == null) {
            return Collections.emptyList();
        }

        List<Skill> manifests = skillConfig.all();
        return manifests != null ? manifests : Collections.emptyList();
    }

    /** 无 SkillCatalog 时仅投影 Active（测试便捷）。 */
    public static TurnBindings bind(ToolCatalog toolConfig, ActiveSkill activeSkill) {
        return bind(toolConfig, null, activeSkill);
    }
}

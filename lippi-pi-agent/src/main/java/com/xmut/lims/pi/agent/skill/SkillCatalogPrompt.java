package com.xmut.lims.pi.agent.skill;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Skills 目录提示组装器。
 * 功能描述：生成 Stable skills 槽的目录摘要与 read_skill 指引。
 */
public final class SkillCatalogPrompt {

    public static final String READ_SKILL_HINT =
            "If the active skill body is not already in a recent tool result, "
                    + "call read_skill once with skill_id, then follow that body and output the final answer. "
                    + "If the skill body is already present in tool results, do not call read_skill again. "
                    + "Do not invent skill content.";

    private SkillCatalogPrompt() {}

    /**
     * @param available 本轮目录（Active 时通常单元素；无 Active 时为全量）
     * @param activeSkillId 已激活 id；可空
     * @return 写入 SKILLS 的文本；无可列则 null
     */
    public static String build(List<Skill> available, String activeSkillId) {
        if (available == null || available.isEmpty()) {
            if (StringUtils.hasText(activeSkillId)) {
                return "Active skill: " + activeSkillId.trim() + ". " + READ_SKILL_HINT;
            }
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Skills catalog\n");
        for (Skill m : available) {
            if (m == null || !StringUtils.hasText(m.getId())) {
                continue;
            }
            sb.append("- ").append(m.getId().trim());
            if (StringUtils.hasText(m.getDescription())) {
                sb.append(": ").append(m.getDescription().trim());
            }
            sb.append('\n');
        }
        if (StringUtils.hasText(activeSkillId)) {
            sb.append("\nActive skill: ").append(activeSkillId.trim()).append('.');
        }
        sb.append('\n').append(READ_SKILL_HINT);
        return sb.toString().trim();
    }
}

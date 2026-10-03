package com.xmut.forma.pi.agent.skill;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Skills 目录提示组装器。
 * 功能描述：生成 Stable skills 槽的目录摘要与 read_skill 指引。
 */
public final class SkillCatalogPrompt {

    public static final String READ_SKILL_HINT =
            "若近期工具结果中尚无当前技能正文，先调用一次 read_skill（传入 skill_id），再按正文执行并给出最终答案；"
                    + "若工具结果中已有技能正文，勿再次调用 read_skill。"
                    + "禁止编造技能内容。";

    private SkillCatalogPrompt() {}

    /**
     * @param available 本轮目录（Active 时通常单元素；无 Active 时为全量）
     * @param activeSkillId 已激活 id；可空
     * @return 写入 SKILLS 的文本；无可列则 null
     */
    public static String build(List<Skill> available, String activeSkillId) {
        if (available == null || available.isEmpty()) {
            if (StringUtils.hasText(activeSkillId)) {
                return "当前技能：" + activeSkillId.trim() + "。" + READ_SKILL_HINT;
            }
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 技能目录\n");
        for (Skill m : available) {
            if (m == null || !StringUtils.hasText(m.getId())) {
                continue;
            }
            sb.append("- ").append(m.getId().trim());
            if (StringUtils.hasText(m.getDescription())) {
                // 目录一行一条：折叠空白，避免 >- 多行描述把版面撑乱
                sb.append(": ").append(m.getDescription().trim().replaceAll("\\s+", " "));
            }
            sb.append('\n');
        }
        if (StringUtils.hasText(activeSkillId)) {
            sb.append("\n当前技能：").append(activeSkillId.trim()).append('。');
        }
        sb.append('\n').append(READ_SKILL_HINT);
        return sb.toString().trim();
    }
}

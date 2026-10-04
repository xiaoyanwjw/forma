package com.xmut.forma.application.business.artifact;

import com.xmut.forma.domain.business.artifact.ArtifactHistoryExcludeCodes;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史排除：chat + 各 Skill {@link Skill#getHideFromHistory()}。
 */
@Component
@RequiredArgsConstructor
public class SkillBackedHistoryExcludeCodes implements ArtifactHistoryExcludeCodes {

    /** 无 Skill 聊天草稿，与成果类型码 {@code chat} 一致。 */
    private static final String CODE_CHAT = "chat";

    private final SkillCatalog skillCatalog;

    @Override
    public List<String> codes() {
        Set<String> out = new LinkedHashSet<String>();
        out.add(CODE_CHAT);
        if (skillCatalog == null) {
            return new ArrayList<String>(out);
        }
        List<Skill> skills = skillCatalog.all();
        if (skills == null) {
            return new ArrayList<String>(out);
        }
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            if (skill == null || skill.getHideFromHistory() == null) {
                continue;
            }
            List<String> hidden = skill.getHideFromHistory();
            for (int j = 0; j < hidden.size(); j++) {
                String code = hidden.get(j);
                if (StringUtils.hasText(code)) {
                    out.add(code.trim().toLowerCase());
                }
            }
        }
        return new ArrayList<String>(out);
    }
}

package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.common.output.TurnAttachmentProvider;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 按 Skill 目录为本轮装箱交付路径。
 * 功能描述：只认 catalog 的一条 output（view 相对路径）。
 * 关键设计：slot 失败则 empty。
 */
public final class CatalogTurnAttachmentProvider implements TurnAttachmentProvider {

    private final SkillCatalog catalog;

    public CatalogTurnAttachmentProvider(SkillCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    @SuppressWarnings("unused")
    public TurnAttachment of(String skillId, String resumeOptionId) {
        if (!StringUtils.hasText(skillId)) {
            return TurnAttachment.empty();
        }

        Optional<Skill> found = catalog.get(skillId);
        if (!found.isPresent()) {
            return TurnAttachment.empty();
        }

        String output = TurnReminder.slot(found.get().getOutput());
        if (output == null) {
            return TurnAttachment.empty();
        }

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put(TurnDeliverableKeys.OUTPUT, output);
        return TurnAttachment.of(raw);
    }
}

package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.extension.output.WorkspaceOutputReader;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Objects;

/**
 * 上架素材两段成果读盘。
 * 功能描述：ecommerce-skulist 策划读 plan 槽，确认执行读 exec 槽。
 * 关键设计：只在目录声明了本阶段两个路径时接手；未声明则不读盘。声明了却缺文件必须抛错。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SkulistOutputParser implements OutputParser {

    static final String SKILL_ID = "ecommerce-skulist";
    private static final String CONFIRM = "confirm_execute";

    private final SkillCatalog catalog;
    private final WorkspaceOutputReader reader;

    public SkulistOutputParser(SkillCatalog catalog) {
        this(catalog, new WorkspaceOutputReader());
    }

    SkulistOutputParser(SkillCatalog catalog, WorkspaceOutputReader reader) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public boolean appliesTo(OutputParseContext ctx) {
        if (ctx == null || !SKILL_ID.equals(trim(ctx.getSkillId()))) {
            return false;
        }
        Skill skill = catalog.get(SKILL_ID).orElse(null);
        if (skill == null) {
            return false;
        }
        boolean exec = CONFIRM.equals(ctx.getResumeOptionId());
        return StringUtils.hasText(phaseView(skill, exec)) && StringUtils.hasText(phaseArtifact(skill, exec));
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        boolean exec = ctx != null && CONFIRM.equals(ctx.getResumeOptionId());
        Skill skill = ctx == null ? null : catalog.get(SKILL_ID).orElse(null);
        String viewRel = skill == null ? null : trim(phaseView(skill, exec));
        String artifactRel = skill == null ? null : trim(phaseArtifact(skill, exec));
        if (!StringUtils.hasText(viewRel)) {
            throw new IllegalArgumentException("output file missing: " + (exec ? "view.json" : "plan/view.json"));
        }
        if (!StringUtils.hasText(artifactRel)) {
            throw new IllegalArgumentException("output file missing: "
                    + (exec ? "artifact.json" : "plan/artifact.json"));
        }
        return reader.read(ctx == null ? null : ctx.getWorkspaceRoot(), viewRel, artifactRel);
    }

    private static String phaseView(Skill skill, boolean exec) {
        return exec ? skill.getViewPath() : skill.getPlanViewPath();
    }

    private static String phaseArtifact(Skill skill, boolean exec) {
        return exec ? skill.getArtifactPath() : skill.getPlanArtifactPath();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

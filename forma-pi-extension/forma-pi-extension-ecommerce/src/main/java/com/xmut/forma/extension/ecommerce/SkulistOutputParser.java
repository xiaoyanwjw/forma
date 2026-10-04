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
 * 关键设计：优先级高于单槽 parser；缺文件抛错，不退回闲聊。
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
        return ctx != null && SKILL_ID.equals(trim(ctx.getSkillId()));
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        boolean exec = ctx != null && CONFIRM.equals(ctx.getResumeOptionId());
        Skill skill = catalog.get(SKILL_ID).orElse(null);
        String viewRel = skill == null ? null : trim(exec ? skill.getViewPath() : skill.getPlanViewPath());
        String artifactRel = skill == null ? null : trim(exec ? skill.getArtifactPath() : skill.getPlanArtifactPath());
        if (!StringUtils.hasText(viewRel)) {
            throw new IllegalArgumentException("output file missing: " + (exec ? "view.json" : "plan/view.json"));
        }
        if (!StringUtils.hasText(artifactRel)) {
            throw new IllegalArgumentException("output file missing: "
                    + (exec ? "artifact.json" : "plan/artifact.json"));
        }
        return reader.read(ctx == null ? null : ctx.getWorkspaceRoot(), viewRel, artifactRel);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}

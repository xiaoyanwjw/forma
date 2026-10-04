package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import org.springframework.core.annotation.Order;

import java.util.Objects;

/**
 * 单槽 Skill 的成果读盘。
 * 功能描述：目录声明了 view 与 artifact、且没有 plan 槽时接手，从工作区读这两个文件。
 * 关键设计：闲聊未声明路径时 {@code appliesTo} 为 false；声明了却缺文件必须抛错，不能当成闲聊成功。
 */
@Order(0)
public final class WorkspaceOutputParser implements OutputParser {

    private final SkillCatalog catalog;
    private final WorkspaceOutputReader reader;

    public WorkspaceOutputParser(SkillCatalog catalog) {
        this(catalog, new WorkspaceOutputReader());
    }

    WorkspaceOutputParser(SkillCatalog catalog, WorkspaceOutputReader reader) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public boolean appliesTo(OutputParseContext ctx) {
        Skill skill = declaredSkill(ctx);
        if (skill == null || StringUtils.hasText(skill.getPlanViewPath())) {
            return false;
        }
        return StringUtils.hasText(skill.getViewPath()) && StringUtils.hasText(skill.getArtifactPath());
    }

    @Override
    public ParsedGenerationOutput parse(OutputParseContext ctx) {
        Skill skill = declaredSkill(ctx);
        if (skill == null) {
            throw new IllegalArgumentException("output file missing: view.json");
        }
        return reader.read(ctx.getWorkspaceRoot(), skill.getViewPath().trim(), skill.getArtifactPath().trim());
    }

    private Skill declaredSkill(OutputParseContext ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getSkillId())) {
            return null;
        }
        return catalog.get(ctx.getSkillId().trim()).orElse(null);
    }
}

package com.xmut.lims.pi.agent.tool;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillCatalogProperties;
import com.xmut.lims.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSkillToolHandlerTest {

    @Test
    void reads_picklist_md_via_prompt_ref() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.registerBootstrap(Skill.builder()
                .id("ecommerce-picklist")
                .description("Picklist")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .build());
        skills.sealBootstrap();

        ReadSkill handler = new ReadSkill(skills);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("skill_id", "ecommerce-picklist");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "read_skill", args),
                new ToolContext("r1", "tr1", "ecommerce-picklist"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("# Skill ecommerce-picklist");
        assertThat(result.getOutput()).contains("非实时平台全站行情");
        assertThat(result.getOutput()).contains("# Reference: output.md");
        assertThat(result.getOutput()).contains("including references");
    }

    @Test
    void rejects_skill_id_not_matching_active() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(Skill.builder()
                .id("ecommerce-picklist")
                .description("pick-body")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Collections.emptyList())
                .build());
        skills.register(Skill.builder()
                .id("other.skill")
                .description("other-body")
                .promptRef("classpath:scenes/other/other.skill/SKILL.md")
                .allowedTools(Collections.emptyList())
                .build());

        ReadSkill handler = new ReadSkill(skills);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("skill_id", "other.skill");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "read_skill", args),
                new ToolContext("r1", "tr1", "ecommerce-picklist"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("skill_id not active");
    }

    @Test
    void resolve_body_requires_prompt_ref() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        Skill m = Skill.builder()
                .id("x")
                .description("inline-body")
                .promptRef("classpath:skills/does-not-exist.md")
                .allowedTools(Collections.emptyList())
                .build();
        skills.register(m);

        assertThat(new ReadSkill(skills).resolveBody(m)).isEmpty();
    }

    @Test
    void fails_when_skill_missing() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        skills.sealBootstrap();
        ReadSkill handler = new ReadSkill(skills);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("skill_id", "nope");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "read_skill", args),
                new ToolContext(null, null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("skill not found");
    }
}

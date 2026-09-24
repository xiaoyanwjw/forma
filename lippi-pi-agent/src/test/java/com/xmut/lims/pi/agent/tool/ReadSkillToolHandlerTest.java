package com.xmut.lims.pi.agent.tool;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.tool.handler.ReadSkill;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSkillToolHandlerTest {

    @Test
    void reads_certificate_ocr_md_via_prompt_ref() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
        skills.registerBootstrap(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .modelUseCase("certificate-ocr")
                .build());
        skills.sealBootstrap();

        ReadSkill handler = new ReadSkill(skills);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("skill_id", "certificate.ocr");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "read_skill", args),
                new ToolContext("r1", "tr1", "certificate.ocr"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).contains("# Skill certificate.ocr");
        assertThat(result.getOutput()).contains("certType");
    }

    @Test
    void rejects_skill_id_not_matching_active() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        skills.register(SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .skillsPrompt("ocr-body")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build());
        skills.register(SkillManifest.builder()
                .id("other.skill")
                .version("1.0.0")
                .skillsPrompt("other-body")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());

        ReadSkill handler = new ReadSkill(skills);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("skill_id", "other.skill");
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", "read_skill", args),
                new ToolContext("r1", "tr1", "certificate.ocr"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("skill_id not active");
    }

    @Test
    void resolve_body_falls_back_to_inline_skills_prompt() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        SkillManifest m = SkillManifest.builder()
                .id("x")
                .version("1")
                .skillsPrompt("inline-body")
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build();
        skills.register(m);

        assertThat(new ReadSkill(skills).resolveBody(m)).contains("inline-body");
    }

    @Test
    void fails_when_skill_missing() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.defaults());
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

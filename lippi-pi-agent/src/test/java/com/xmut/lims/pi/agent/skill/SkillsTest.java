package com.xmut.lims.pi.agent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SkillsTest {

    @Test
    void loadsOfficialFrontmatterAndBodyRef() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-picklist\n"
                + "description: Picklist skill for Adam ecommerce.\n"
                + "allowed-tools: read_skill\n"
                + "---\n"
                + "\n"
                + "# Body\n"
                + "Rules here.\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-picklist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getId()).isEqualTo("ecommerce-picklist");
        assertThat(m.getDescription()).contains("Picklist");
        assertThat(m.getAllowedTools()).containsExactly("read_skill");
        assertThat(m.getSceneCode()).isEqualTo("ecommerce");
        assertThat(m.getPromptRef()).contains("scenes/ecommerce/ecommerce-picklist/SKILL.md");
    }

    @Test
    void parseAllowedTools_splitsCommaSeparatedNames() throws Exception {
        String md = ""
                + "---\n"
                + "name: ecommerce-skulist\n"
                + "description: Listing with HITL.\n"
                + "allowed-tools: ask_human, read_skill\n"
                + "---\n"
                + "\n"
                + "# Body\n";
        Resource resource = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "SKILL.md"; }
            @Override public String getDescription() {
                return "class path resource [scenes/ecommerce/ecommerce-skulist/SKILL.md]";
            }
        };
        Skill m = Skills.parse(resource, "ecommerce");
        assertThat(m.getAllowedTools()).containsExactly("ask_human", "read_skill");
    }
}

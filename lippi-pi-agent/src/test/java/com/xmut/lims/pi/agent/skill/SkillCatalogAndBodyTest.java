package com.xmut.lims.pi.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogAndBodyTest {

    @Test
    void catalog_includes_entries_and_read_skill_hint_not_full_body() {
        Skill m = Skill.builder()
                .id("ecommerce-picklist")
                .description("Extract fields")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .build();

        String text = SkillCatalogPrompt.build(Collections.singletonList(m), "ecommerce-picklist");

        assertThat(text).contains("ecommerce-picklist");
        assertThat(text).contains("Extract fields");
        assertThat(text).contains("Active skill: ecommerce-picklist");
        assertThat(text).contains("read_skill");
        assertThat(text).doesNotContain("非实时平台数据");
    }

    @Test
    void catalog_null_when_empty() {
        assertThat(SkillCatalogPrompt.build(null, null)).isNull();
        assertThat(SkillCatalogPrompt.build(Collections.emptyList(), null)).isNull();
    }
}

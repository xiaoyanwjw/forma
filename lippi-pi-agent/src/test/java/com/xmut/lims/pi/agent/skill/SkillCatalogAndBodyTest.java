package com.xmut.lims.pi.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogAndBodyTest {

    @Test
    void catalog_includes_entries_and_read_skill_hint_not_full_body() {
        SkillManifest m = SkillManifest.builder()
                .id("certificate.ocr")
                .version("1.0.0")
                .displayName("Certificate OCR")
                .description("Extract fields")
                .promptRef("classpath:skills/certificate-ocr.md")
                .toolWhitelist(Collections.singletonList("read_skill"))
                .maxToolLevel(com.xmut.lims.pi.agent.tool.ToolLevel.READ)
                .graphTopology(SkillGraphTopology.SIMPLE_AGENT_END)
                .build();

        String text = SkillCatalogPrompt.build(Collections.singletonList(m), "certificate.ocr");

        assertThat(text).contains("certificate.ocr");
        assertThat(text).contains("Certificate OCR");
        assertThat(text).contains("Extract fields");
        assertThat(text).contains("Active skill: certificate.ocr");
        assertThat(text).contains("read_skill");
        assertThat(text).doesNotContain("certType");
    }

    @Test
    void catalog_null_when_empty() {
        assertThat(SkillCatalogPrompt.build(null, null)).isNull();
        assertThat(SkillCatalogPrompt.build(Collections.emptyList(), null)).isNull();
    }
}

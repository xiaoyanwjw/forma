package com.xmut.forma.pi.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPromptBodyLoaderTest {

    @Test
    void loads_skill_md_and_appends_sorted_references() {
        Optional<String> body = SkillPromptBodyLoader.load(
                "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md");

        assertThat(body).isPresent();
        String text = body.get();
        assertThat(text).contains("# 选品清单");
        assertThat(text).contains("# Reference: output.md");
        assertThat(text).contains("非实时平台全站行情");
        assertThat(text).contains("sourceUrl");
        // Only intentional refs; stale maintainer notes must not ship.
        assertThat(text).doesNotContain("rank-and-artifact");
        assertThat(text.indexOf("## Output"))
                .isLessThan(text.indexOf("# Reference: output.md"));
    }
}

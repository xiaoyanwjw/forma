package com.xmut.lims.pi.agent.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathSkillBootstrapTest {

    @Test
    void load_default_pattern_registers_sample() {
        InMemorySkillConfig config = new InMemorySkillConfig(SkillConfigProperties.defaults());
        int n = ClasspathSkillBootstrap.load(config);
        assertThat(n).isGreaterThanOrEqualTo(1);
        assertThat(config.resolve("certificate.ocr")).isPresent();
        assertThat(config.resolve("certificate.ocr").get().getVersion()).isEqualTo("1.0.0");
    }
}

package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.TurnInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SkillSelectorTest {

    @Test
    void skillId_preferred_over_domain() {
        InMemorySkillCatalog config = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        config.register(Skill.builder()
                .id("a")
                .description("pa")
                .promptRef("classpath:skills/a.md")
                .build());
        config.register(Skill.builder()
                .id("b")
                .description("pb")
                .promptRef("classpath:skills/b.md")
                .build());

        TurnInput req = TurnInput.builder()
                .skillId("a")
                .domain("b")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("hi")))
                .build();

        ActiveSkill active = SkillSelector.select(req, config);
        assertThat(active.isPresent()).isTrue();
        assertThat(active.getId()).isEqualTo("a");
        assertThat(SkillSelector.skillIdOf(req)).isEqualTo("a");
    }

    @Test
    void missing_skill_throws() {
        InMemorySkillCatalog config = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        TurnInput req = TurnInput.builder()
                .skillId("nope")
                .messages(java.util.Collections.singletonList(com.xmut.lims.pi.ai.message.Message.user("hi")))
                .build();
        assertThatThrownBy(() -> SkillSelector.select(req, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown skillId");
    }

    @Test
    void blank_request_returns_none() {
        assertThat(SkillSelector.select(null, null)).isEqualTo(ActiveSkill.NONE);
        assertThat(SkillSelector.skillIdOf(TurnInput.withUser("x").build()))
                .isNull();
    }
}

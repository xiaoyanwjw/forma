package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.TurnInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SkillSelectorTest {

    @Test
    void skillId_preferred_over_domain() {
        InMemorySkillConfig config = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        config.register(SkillManifest.builder()
                .id("a")
                .version("1.0.0")
                .skillsPrompt("pa")
                .maxToolLevel(com.xmut.lims.pi.agent.tool.ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());
        config.register(SkillManifest.builder()
                .id("b")
                .version("1.0.0")
                .skillsPrompt("pb")
                .maxToolLevel(com.xmut.lims.pi.agent.tool.ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
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
        InMemorySkillConfig config = new InMemorySkillConfig(SkillConfigProperties.defaults());
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

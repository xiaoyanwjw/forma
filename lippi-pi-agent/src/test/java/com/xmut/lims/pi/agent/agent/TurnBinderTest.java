package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.ai.model.ToolSchema;
import com.xmut.lims.pi.agent.skill.ActiveSkill;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import com.xmut.lims.pi.agent.skill.SkillGraphTopology;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.tool.DefaultToolConfig;
import com.xmut.lims.pi.agent.tool.ToolBinding;
import com.xmut.lims.pi.agent.tool.ToolLevel;
import com.xmut.lims.pi.agent.tool.ToolManifest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TurnBinderTest {

    @Test
    void none_skill_without_config_projects_tools_only() {
        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Arrays.asList(
                binding("t1", "guidance-1"),
                binding("t2", "guidance-2")));

        TurnBindings bindings = TurnBinder.bind(tools, null, ActiveSkill.NONE);
        Map<String, Object> state = new HashMap<>();
        bindings.applyTo(state);

        assertThat(state).doesNotContainKey(StateKeys.SKILLS);
        assertThat(state).doesNotContainKey(StateKeys.ACTIVE_SKILL_ID);
        assertThat(state).doesNotContainKey(StateKeys.AVAILABLE_SKILLS);
        assertThat(bindings.getToolsText()).contains("guidance-1");
        @SuppressWarnings("unchecked")
        List<ToolSchema> schemas = (List<ToolSchema>) state.get(StateKeys.AVAILABLE_TOOLS);
        assertThat(schemas).extracting(ToolSchema::getName).containsExactly("t1", "t2");
    }

    @Test
    void none_skill_with_config_injects_skill_catalog() {
        DefaultToolConfig tools = DefaultToolConfig.empty();
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        skills.register(skill("a", "A"));
        skills.register(skill("b", "B"));

        Map<String, Object> state = new HashMap<>();
        TurnBindings bindings = TurnBinder.bind(tools, skills, ActiveSkill.NONE);
        bindings.applyTo(state);

        assertThat(bindings.getSkillsText()).contains("a").contains("b");
        assertThat(bindings.getSkillsText()).contains("read_skill");
        assertThat(state).doesNotContainKey(StateKeys.ACTIVE_SKILL_ID);
        @SuppressWarnings("unchecked")
        List<SkillManifest> available = (List<SkillManifest>) state.get(StateKeys.AVAILABLE_SKILLS);
        assertThat(available).extracting(SkillManifest::getId).containsExactly("a", "b");
    }

    @Test
    void active_skill_empty_whitelist_hides_tools() {
        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Collections.singletonList(
                binding("t1", "g")));
        ActiveSkill skill = ActiveSkill.of(skill("s", "skill-body").toBuilder()
                .toolWhitelist(Collections.emptyList())
                .build());

        Map<String, Object> state = new HashMap<>();
        TurnBindings bindings = TurnBinder.bind(tools, null, skill);
        bindings.applyTo(state);

        assertThat(bindings.getSkillsText())
                .contains("s")
                .contains("read_skill")
                .doesNotContain("skill-body");
        assertThat(state.get(StateKeys.ACTIVE_SKILL_ID)).isEqualTo("s");
        assertThat(state).doesNotContainKey(StateKeys.AVAILABLE_TOOLS);
        assertThat(bindings.getToolsText()).isNull();
    }

    @Test
    void active_skill_narrows_available_skills_to_current() {
        InMemorySkillConfig skills = new InMemorySkillConfig(SkillConfigProperties.allowMutation());
        skills.register(skill("a", "A"));
        skills.register(skill("b", "B"));
        ActiveSkill active = ActiveSkill.of(skills.resolve("b").orElseThrow(AssertionError::new));

        Map<String, Object> state = new HashMap<>();
        TurnBindings bindings = TurnBinder.bind(DefaultToolConfig.empty(), skills, active);
        bindings.applyTo(state);

        assertThat(bindings.getSkillsText())
                .contains("b")
                .contains("Active skill: b")
                .doesNotContain("\n- a");
        @SuppressWarnings("unchecked")
        List<SkillManifest> available = (List<SkillManifest>) state.get(StateKeys.AVAILABLE_SKILLS);
        assertThat(available).extracting(SkillManifest::getId).containsExactly("b");
    }

    @Test
    void whitelist_intersects_schemas() {
        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Arrays.asList(
                binding("keep", "k"),
                binding("drop", "d")));
        ActiveSkill skill = ActiveSkill.of(SkillManifest.builder()
                .id("s")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(Collections.singletonList("keep"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());

        Map<String, Object> state = new HashMap<>();
        TurnBindings bindings = TurnBinder.bind(tools, null, skill);
        bindings.applyTo(state);

        @SuppressWarnings("unchecked")
        List<ToolSchema> schemas = (List<ToolSchema>) state.get(StateKeys.AVAILABLE_TOOLS);
        assertThat(schemas).extracting(ToolSchema::getName).containsExactly("keep");
        assertThat(bindings.getToolsText()).isEqualTo("k");
    }

    @Test
    void whitelist_clips_tools_text_to_intersection() {
        DefaultToolConfig tools = DefaultToolConfig.ofBindings(Arrays.asList(
                binding("keep", "keep-text"),
                binding("drop", "drop-text")));
        ActiveSkill skill = ActiveSkill.of(SkillManifest.builder()
                .id("s")
                .version("1.0.0")
                .skillsPrompt("p")
                .toolWhitelist(Collections.singletonList("keep"))
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build());

        Map<String, Object> state = new HashMap<>();
        TurnBindings bindings = TurnBinder.bind(tools, null, skill);
        bindings.applyTo(state);

        assertThat(bindings.getToolsText()).isEqualTo("keep-text");
        assertThat(bindings.getToolsText()).doesNotContain("drop");
    }

    private static SkillManifest skill(String id, String prompt) {
        return SkillManifest.builder()
                .id(id)
                .version("1.0.0")
                .skillsPrompt(prompt)
                .toolWhitelist(Collections.emptyList())
                .maxToolLevel(ToolLevel.READ)
                .graphTopology(SkillGraphTopology.TOOL_LOOP)
                .build();
    }

    private static ToolBinding binding(String id, String text) {
        ToolManifest m = ToolManifest.builder()
                .id(id)
                .text(text)
                .level(ToolLevel.READ)
                .schema(ToolSchema.builder().name(id).description(id).build())
                .build();
        return ToolBinding.of(m, null);
    }
}

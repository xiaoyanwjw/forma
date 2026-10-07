package com.xmut.forma.application.business.scene.pack;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneCapabilityPackLoaderTest {

    @Test
    void loadEcommerceRequiresBothSkillsFromSkillCatalog() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("ecommerce")).thenReturn(Arrays.asList(picklist(), skulist()));
        SceneCapabilityPackLoader loader = newLoader(skills, ecommerceMeta());

        SceneCapabilityPack pack = loader.load("ecommerce");

        assertThat(pack.getSceneCode()).isEqualTo("ecommerce");
        assertThat(pack.hasSkill("ecommerce-picklist")).isTrue();
        assertThat(pack.hasSkill("ecommerce-skulist")).isTrue();
        assertThat(pack.findSkill("ecommerce-picklist").get().getPromptRef())
                .contains("ecommerce-picklist/SKILL.md");
        assertThat(pack.findSkill("ecommerce-skulist").get().getPromptRef())
                .contains("ecommerce-skulist/SKILL.md");
    }

    @Test
    void loadFailsWhenPicklistMissing() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("ecommerce")).thenReturn(Collections.singletonList(skulist()));
        SceneCapabilityPackLoader loader = newLoader(skills, ecommerceMeta());

        assertThatThrownBy(() -> loader.load("ecommerce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadUnknownSceneCodeFailsWithHumanMessage() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("no_such_scene")).thenReturn(Collections.emptyList());
        SceneMetaCatalog metas = mock(SceneMetaCatalog.class);
        when(metas.find("no_such_scene")).thenReturn(Optional.empty());
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills, metas);

        assertThatThrownBy(() -> loader.load("no_such_scene"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadFailsWhenPackYamlMissingEvenIfSkillsExist() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("ecommerce")).thenReturn(Arrays.asList(picklist(), skulist()));
        SceneMetaCatalog metas = mock(SceneMetaCatalog.class);
        when(metas.find("ecommerce")).thenReturn(Optional.empty());
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills, metas);

        assertThatThrownBy(() -> loader.load("ecommerce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadXiaohongshuRequiresThreeSkillsFromSkillCatalog() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("xiaohongshu")).thenReturn(Arrays.asList(
                xhsSkill("xhs-topiclist"), xhsSkill("xhs-note"), xhsSkill("xhs-break")));
        SceneCapabilityPackLoader loader = newLoader(skills, xhsMeta());

        SceneCapabilityPack pack = loader.load("xiaohongshu");

        assertThat(pack.getSceneCode()).isEqualTo("xiaohongshu");
        assertThat(pack.hasSkill("xhs-topiclist")).isTrue();
        assertThat(pack.hasSkill("xhs-note")).isTrue();
        assertThat(pack.hasSkill("xhs-break")).isTrue();
    }

    @Test
    void loadFailsWhenXhsNoteMissing() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("xiaohongshu")).thenReturn(Arrays.asList(
                xhsSkill("xhs-topiclist"), xhsSkill("xhs-break")));
        SceneCapabilityPackLoader loader = newLoader(skills, xhsMeta());

        assertThatThrownBy(() -> loader.load("xiaohongshu"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadTechDigestRequiresSkillFromCatalog() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("tech_digest")).thenReturn(Collections.singletonList(
                Skill.builder().id("tech-digest").description("d")
                        .promptRef("classpath:scenes/tech_digest/tech-digest/SKILL.md")
                        .allowedTools(Collections.singletonList("read_skill"))
                        .sceneCode("tech_digest").build()));
        SceneCapabilityPack pack = newLoader(skills, techMeta()).load("tech_digest");
        assertThat(pack.hasSkill("tech-digest")).isTrue();
    }

    @Test
    void loadTechDigestFailsWhenSkillMissing() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("tech_digest")).thenReturn(Collections.singletonList(
                Skill.builder().id("other").description("d")
                        .promptRef("classpath:scenes/tech_digest/other/SKILL.md")
                        .allowedTools(Collections.singletonList("read_skill"))
                        .sceneCode("tech_digest").build()));
        assertThatThrownBy(() -> newLoader(skills, techMeta()).load("tech_digest"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadBlankSceneCodeFailsWithHumanMessage() {
        SkillCatalog skills = mock(SkillCatalog.class);
        SceneMetaCatalog metas = mock(SceneMetaCatalog.class);
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills, metas);

        assertThatThrownBy(() -> loader.load("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    private static SceneCapabilityPackLoader newLoader(SkillCatalog skills, SceneMeta meta) {
        SceneMetaCatalog metas = mock(SceneMetaCatalog.class);
        when(metas.find(meta.getSceneCode())).thenReturn(Optional.of(meta));
        return new SceneCapabilityPackLoader(skills, metas);
    }

    private static SceneMeta ecommerceMeta() {
        return new SceneMeta("ecommerce",
                Arrays.asList("ecommerce-picklist", "ecommerce-skulist"),
                "ecommerce-picklist");
    }

    private static SceneMeta xhsMeta() {
        return new SceneMeta("xiaohongshu",
                Arrays.asList("xhs-topiclist", "xhs-note", "xhs-break"),
                "xhs-topiclist");
    }

    private static SceneMeta techMeta() {
        return new SceneMeta("tech_digest",
                Collections.singletonList("tech-digest"),
                "tech-digest");
    }

    private static Skill picklist() {
        return Skill.builder()
                .id("ecommerce-picklist")
                .description("ecommerce-picklist")
                .promptRef("classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")
                .allowedTools(Arrays.asList("read_skill", "search_sku"))
                .sceneCode("ecommerce")
                .build();
    }

    private static Skill skulist() {
        return skill("ecommerce-skulist", "classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md");
    }

    private static Skill skill(String id, String promptRef) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef(promptRef)
                .allowedTools(Collections.singletonList("read_skill"))
                .sceneCode("ecommerce")
                .build();
    }

    private static Skill xhsSkill(String id) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef("classpath:scenes/xiaohongshu/" + id + "/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .sceneCode("xiaohongshu")
                .build();
    }
}

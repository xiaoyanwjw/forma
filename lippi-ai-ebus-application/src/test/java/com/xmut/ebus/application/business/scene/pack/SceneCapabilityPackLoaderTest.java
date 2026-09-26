package com.xmut.ebus.application.business.scene.pack;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneCapabilityPackLoaderTest {

    @Test
    void loadEcommerceRequiresBothSkillsFromSkillCatalog() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("ecommerce")).thenReturn(Arrays.asList(picklist(), skulist()));
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills);

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
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills);

        assertThatThrownBy(() -> loader.load("ecommerce"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadUnknownSceneCodeFailsWithHumanMessage() {
        SkillCatalog skills = mock(SkillCatalog.class);
        when(skills.listByScene("no_such_scene")).thenReturn(Collections.emptyList());
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills);

        assertThatThrownBy(() -> loader.load("no_such_scene"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    @Test
    void loadBlankSceneCodeFailsWithHumanMessage() {
        SkillCatalog skills = mock(SkillCatalog.class);
        SceneCapabilityPackLoader loader = new SceneCapabilityPackLoader(skills);

        assertThatThrownBy(() -> loader.load("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE);
    }

    private static Skill picklist() {
        return skill("ecommerce-picklist", "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md");
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
}

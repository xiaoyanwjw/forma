package com.xmut.forma.application.business.scene.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.application.business.scene.dto.SceneSkillCapsuleItemDTO;
import com.xmut.forma.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SceneSkillCapsuleLoaderTest {

    private final SceneSkillCapsuleLoader loader =
            new SceneSkillCapsuleLoader(new DefaultResourceLoader(), new ObjectMapper());

    @Test
    void launchLocationReplacesSkillMd() {
        assertThat(SceneSkillCapsuleLoader.launchLocation(
                "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md"))
                .isEqualTo("classpath:scenes/ecommerce/ecommerce-picklist/launch.json");
    }

    @Test
    void launchLocationForTechDigestSkillMd() {
        assertThat(SceneSkillCapsuleLoader.launchLocation(
                "classpath:scenes/tech_digest/tech-digest/SKILL.md"))
                .isEqualTo("classpath:scenes/tech_digest/tech-digest/launch.json");
    }

    @Test
    void loadForReadsTechDigestLaunchJson() {
        List<SceneSkillCapsuleItemDTO> items = loader.loadFor(Collections.singletonList(
                skill("tech-digest", "classpath:scenes/tech_digest/tech-digest/SKILL.md")));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkillId()).isEqualTo("tech-digest");
        assertThat(items.get(0).getLabel()).isEqualTo("科技速读");
        assertThat(items.get(0).getExamplePrompt()).contains("example.com/product");
        assertThat(items.get(0).getSortOrder()).isEqualTo(1);
    }

    @Test
    void loadForReadsClasspathLaunchJsonInSortOrder() {
        List<SceneSkillCapsuleItemDTO> items = loader.loadFor(Arrays.asList(
                skill("ecommerce-skulist", "classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md"),
                skill("ecommerce-picklist", "classpath:scenes/ecommerce/ecommerce-picklist/SKILL.md")));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getSkillId()).isEqualTo("ecommerce-picklist");
        assertThat(items.get(0).getLabel()).isEqualTo("选品清单");
        assertThat(items.get(0).getExamplePrompt()).contains("Mac Mini 配件");
        assertThat(items.get(0).getSortOrder()).isEqualTo(1);
        assertThat(items.get(1).getSkillId()).isEqualTo("ecommerce-skulist");
        assertThat(items.get(1).getLabel()).isEqualTo("生成素材");
    }

    @Test
    void loadForSkipsSkillWithoutLaunchJson() {
        List<SceneSkillCapsuleItemDTO> items = loader.loadFor(Collections.singletonList(
                skill("ghost", "classpath:scenes/ecommerce/no-such-skill/SKILL.md")));

        assertThat(items).isEmpty();
    }

    private static Skill skill(String id, String promptRef) {
        return Skill.builder()
                .id(id)
                .description("d")
                .promptRef(promptRef)
                .sceneCode("ecommerce")
                .build();
    }
}

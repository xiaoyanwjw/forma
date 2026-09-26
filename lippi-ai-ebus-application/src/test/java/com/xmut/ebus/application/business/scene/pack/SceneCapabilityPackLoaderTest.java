package com.xmut.ebus.application.business.scene.pack;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneCapabilityPackLoaderTest {

    private final SceneCapabilityPackLoader loader =
            new SceneCapabilityPackLoader(new PathMatchingResourcePatternResolver());

    @Test
    void loadEcommercePackContainsPicklistAndSkulist() {
        SceneCapabilityPack pack = loader.load("ecommerce");

        assertEquals("ecommerce", pack.getSceneCode());
        assertTrue(pack.hasSkill(SceneCapabilityPackLoader.SKILL_PICKLIST));
        assertTrue(pack.hasSkill(SceneCapabilityPackLoader.SKILL_SKULIST));

        SkillManifest picklist = pack.findSkill(SceneCapabilityPackLoader.SKILL_PICKLIST).get();
        assertEquals("1.0.0", picklist.getVersion());
        assertTrue(picklist.getPromptRef().contains("ecommerce.picklist.md"));

        SkillManifest skulist = pack.findSkill(SceneCapabilityPackLoader.SKILL_SKULIST).get();
        assertEquals("1.0.0", skulist.getVersion());
        assertTrue(skulist.getPromptRef().contains("ecommerce.skulist.md"));
    }

    @Test
    void loadEcommercePackWithOnlyOneSkillFailsWithHumanMessage() {
        ResourcePatternResolver stub = new PathMatchingResourcePatternResolver() {
            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                return new Resource[] {
                        new ClassPathResource("scenes/ecommerce/ecommerce.picklist.skill.json")
                };
            }
        };
        SceneCapabilityPackLoader oneSkillLoader = new SceneCapabilityPackLoader(stub);

        BusinessException ex = assertThrows(BusinessException.class, () -> oneSkillLoader.load("ecommerce"));
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, ex.getMessage());
    }

    @Test
    void loadUnknownSceneCodeFailsWithHumanMessage() {
        BusinessException ex = assertThrows(BusinessException.class, () -> loader.load("no_such_scene"));
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, ex.getMessage());
    }

    @Test
    void loadBlankSceneCodeFailsWithHumanMessage() {
        BusinessException ex = assertThrows(BusinessException.class, () -> loader.load("  "));
        assertEquals(SceneCapabilityPackLoader.MSG_PACK_UNAVAILABLE, ex.getMessage());
    }
}

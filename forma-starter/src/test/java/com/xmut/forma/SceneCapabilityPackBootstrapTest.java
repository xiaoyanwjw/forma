package com.xmut.forma;

import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalogProperties;
import com.xmut.forma.pi.agent.skill.Skills;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 starter 官方 SKILL.md + pack.yaml 可被注册，并由薄 Loader 按 sceneCode 选型。
 */
class SceneCapabilityPackBootstrapTest {

    @Test
    void starterEcommerceSkillsRegisterIntoSkillCatalog() {
        SkillCatalog skills = loadStarterSkills();

        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_PICKLIST).isPresent());
        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_SKULIST).isPresent());
    }

    @Test
    void starterEcommercePackLoadsViaLoader() {
        SceneCapabilityPackLoader loader = newLoader(loadStarterSkills());
        assertTrue(loader.load("ecommerce").hasSkill(SceneCapabilityPackLoader.SKILL_PICKLIST));
        assertTrue(loader.load("ecommerce").hasSkill(SceneCapabilityPackLoader.SKILL_SKULIST));
    }

    @Test
    void starterXiaohongshuSkillsRegisterAndPackLoads() {
        SkillCatalog skills = loadStarterSkills();
        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST).isPresent());
        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_XHS_NOTE).isPresent());
        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_XHS_BREAK).isPresent());

        SceneCapabilityPackLoader loader = newLoader(skills);
        assertTrue(loader.load("xiaohongshu").hasSkill(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST));
        assertTrue(loader.load("xiaohongshu").hasSkill(SceneCapabilityPackLoader.SKILL_XHS_NOTE));
        assertTrue(loader.load("xiaohongshu").hasSkill(SceneCapabilityPackLoader.SKILL_XHS_BREAK));
    }

    private static SceneCapabilityPackLoader newLoader(SkillCatalog skills) {
        return new SceneCapabilityPackLoader(skills);
    }

    private static SkillCatalog loadStarterSkills() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        Skills.loadFromClasspath(skills);
        skills.sealBootstrap();
        return skills;
    }
}

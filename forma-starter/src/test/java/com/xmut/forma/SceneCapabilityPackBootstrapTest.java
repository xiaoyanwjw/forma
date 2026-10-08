package com.xmut.forma;

import com.xmut.forma.application.business.scene.pack.ClasspathSceneMetaCatalog;
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

        assertTrue(skills.resolve("ecommerce-picklist").isPresent());
        assertTrue(skills.resolve("ecommerce-skulist").isPresent());
    }

    @Test
    void starterEcommercePackLoadsViaLoader() {
        SceneCapabilityPackLoader loader = newLoader(loadStarterSkills());
        assertTrue(loader.load("ecommerce").hasSkill("ecommerce-picklist"));
        assertTrue(loader.load("ecommerce").hasSkill("ecommerce-skulist"));
    }

    @Test
    void starterXiaohongshuSkillsRegisterAndPackLoads() {
        SkillCatalog skills = loadStarterSkills();
        assertTrue(skills.resolve("xhs-topiclist").isPresent());
        assertTrue(skills.resolve("xhs-note").isPresent());
        assertTrue(skills.resolve("xhs-break").isPresent());

        SceneCapabilityPackLoader loader = newLoader(skills);
        assertTrue(loader.load("xiaohongshu").hasSkill("xhs-topiclist"));
        assertTrue(loader.load("xiaohongshu").hasSkill("xhs-note"));
        assertTrue(loader.load("xiaohongshu").hasSkill("xhs-break"));
    }

    @Test
    void starterTechProductCompetitorAndBriefingRegisterAndPackLoads() {
        SkillCatalog skills = loadStarterSkills();
        assertTrue(skills.resolve("tech-competitor").isPresent());
        assertTrue(skills.resolve("tech-briefing").isPresent());

        SceneCapabilityPackLoader loader = newLoader(skills);
        assertTrue(loader.load("tech_product").hasSkill("tech-competitor"));
        assertTrue(loader.load("tech_product").hasSkill("tech-briefing"));
    }

    private static SceneCapabilityPackLoader newLoader(SkillCatalog skills) {
        return new SceneCapabilityPackLoader(skills, new ClasspathSceneMetaCatalog());
    }

    private static SkillCatalog loadStarterSkills() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.defaults());
        Skills.loadFromClasspath(skills);
        skills.sealBootstrap();
        return skills;
    }
}

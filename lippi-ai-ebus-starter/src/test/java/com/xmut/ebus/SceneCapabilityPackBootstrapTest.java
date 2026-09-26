package com.xmut.ebus;

import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.application.config.EbusSkillConfiguration;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 starter 资源包可被 SkillConfig 二次扫描注册（AD-16）。
 */
class SceneCapabilityPackBootstrapTest {

    @Test
    void starterEcommerceSkillsRegisterIntoSkillConfig() {
        SkillConfig skills = new EbusSkillConfiguration().skillConfig(
                new PathMatchingResourcePatternResolver(),
                SkillConfigProperties.defaults());

        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_PICKLIST).isPresent());
        assertTrue(skills.resolve(SceneCapabilityPackLoader.SKILL_SKULIST).isPresent());
    }

    @Test
    void starterEcommercePackLoadsViaLoader() {
        SceneCapabilityPackLoader loader =
                new SceneCapabilityPackLoader(new PathMatchingResourcePatternResolver());
        assertTrue(loader.load("ecommerce").hasSkill(SceneCapabilityPackLoader.SKILL_PICKLIST));
        assertTrue(loader.load("ecommerce").hasSkill(SceneCapabilityPackLoader.SKILL_SKULIST));
    }
}

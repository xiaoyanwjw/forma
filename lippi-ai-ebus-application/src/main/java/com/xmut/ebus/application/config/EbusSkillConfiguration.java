package com.xmut.ebus.application.config;

import com.xmut.lims.pi.agent.skill.ClasspathSkillBootstrap;
import com.xmut.lims.pi.agent.skill.InMemorySkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * 覆盖 pi-agent 默认 SkillConfig：在密封前额外注册场景目录下的 skill JSON。
 * <p>
 * 不改 ClasspathSkillBootstrap 全局默认 pattern；场景包薄封装二次扫描。
 */
@Configuration
public class EbusSkillConfiguration {

    /** 场景能力包 skill 清单扫描 pattern（AD-16）：classpath*:scenes/&lt;code&gt;/*.skill.json */
    public static final String SCENE_SKILL_PATTERN = "classpath*:scenes/*/*.skill.json";

    @Bean
    public SkillConfig skillConfig(ResourcePatternResolver resourcePatternResolver,
                                   SkillConfigProperties skillConfigProperties) {
        InMemorySkillConfig config = new InMemorySkillConfig(skillConfigProperties);
        ClasspathSkillBootstrap.load(config, resourcePatternResolver);
        ClasspathSkillBootstrap.load(config, resourcePatternResolver, SCENE_SKILL_PATTERN);
        config.sealBootstrap();
        return config;
    }
}

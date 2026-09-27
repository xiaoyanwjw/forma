package com.xmut.ebus.application.business.computer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Registers {@link ComputerViewResolver} with a fixed projector order.
 */
@Configuration
public class ComputerViewConfiguration {

    @Bean
    public ComputerViewResolver computerViewResolver(NormalizeViewProjector normalizeViewProjector,
                                                     NoSkillMarkdownProjector noSkillMarkdownProjector) {
        return new ComputerViewResolver(Arrays.asList(
                normalizeViewProjector,
                noSkillMarkdownProjector));
    }
}

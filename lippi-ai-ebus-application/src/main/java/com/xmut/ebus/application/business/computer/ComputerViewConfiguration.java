package com.xmut.ebus.application.business.computer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Registers the Computer view strategy chain in fixed order.
 */
@Configuration
public class ComputerViewConfiguration {

    @Bean
    public ViewProjectorChain viewProjectorChain(NormalizeViewProjector normalizeViewProjector,
                                                 LegacyPicklistFallbackProjector legacyPicklistFallbackProjector,
                                                 NoSkillMarkdownProjector noSkillMarkdownProjector) {
        return new ViewProjectorChain(Arrays.asList(
                normalizeViewProjector,
                legacyPicklistFallbackProjector,
                noSkillMarkdownProjector));
    }
}

package com.xmut.forma.application.business.artifact;

import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalogProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBackedHistoryExcludeCodesTest {

    @Test
    void includesChatAndSkillHideFromHistory() {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        catalog.register(Skill.builder()
                .id("ecommerce-skulist")
                .description("d")
                .promptRef("classpath:x")
                .allowedTools(Collections.singletonList("read_skill"))
                .persistAs("sku")
                .hideFromHistory(Collections.singletonList("listing_plan"))
                .build());
        SkillBackedHistoryExcludeCodes excludes = new SkillBackedHistoryExcludeCodes(catalog);
        assertTrue(excludes.codes().contains("chat"));
        assertTrue(excludes.codes().contains("listing_plan"));
        assertEquals(2, excludes.codes().size());
    }
}

package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalogProperties;
import com.xmut.forma.pi.agent.skill.Skills;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRunProfileTest {

    @Test
    void blankSkillIdResolvesToNoSkill() throws Exception {
        SkillRunProfile profile = SkillRunProfile.resolve(catalog(), null, false);
        assertFalse(profile.isSkillBound());
        assertTrue(profile.isSettleEnabled());
        assertFalse(profile.isDryRun());
        assertNull(profile.getSkillId());
        assertEquals(SkillRunProfile.PERSIST_NONE, profile.getPersistAs());
        assertTrue(profile.isRequireUserText());
    }

    @Test
    void dryRunStillBindsDefaultSkill() throws Exception {
        SkillRunProfile profile = SkillRunProfile.resolve(catalog(), null, true);
        assertTrue(profile.isDryRun());
        assertTrue(profile.isSkillBound());
        assertEquals("ecommerce-picklist", profile.getSkillId());
    }

    @Test
    void persistAsComesFromSkillMd() throws Exception {
        Skill skill = Skills.parse(
                new ClassPathResource("scenes/ecommerce/ecommerce-skulist/SKILL.md"), "ecommerce");
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(skill);
        SkillRunProfile profile = SkillRunProfile.resolve(skills, skill.getId(), false);
        assertEquals("sku", profile.getPersistAs());
        assertEquals("ecommerce-skulist", profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isRequireUserText());
        assertEquals(skill.getPersistAs(), SkillRunProfile.billed(skill.getId(), skill.getPersistAs()).getPersistAs());
    }

    @Test
    void picklistPersistAsComesFromSkillMd() throws Exception {
        SkillRunProfile profile = SkillRunProfile.resolve(catalogFromMd(), "ecommerce-picklist", false);
        assertEquals("picklist", profile.getPersistAs());
        assertEquals("ecommerce-picklist", profile.getSkillId());
    }

    @Test
    void unknownSkillStillRejected() throws Exception {
        assertThrows(BusinessException.class,
                () -> SkillRunProfile.resolve(catalog(), "unknown-skill", false));
    }

    @Test
    void catalogPersistAsDrivesNewSceneWithoutHardcode() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(billedSkill("future-digest", "future_digest"));
        SkillRunProfile profile = SkillRunProfile.resolve(skills, "future-digest", false);
        assertEquals("future_digest", profile.getPersistAs());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isSkillBound());
    }

    @Test
    void missingPersistAsRejected() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(billedSkill("chatty", null));
        assertThrows(BusinessException.class, () -> SkillRunProfile.resolve(skills, "chatty", false));
    }

    private static SkillCatalog catalog() throws IOException {
        return catalogFromMd();
    }

    private static SkillCatalog catalogFromMd() throws IOException {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(Skills.parse(
                new ClassPathResource("scenes/ecommerce/ecommerce-picklist/SKILL.md"), "ecommerce"));
        skills.register(Skills.parse(
                new ClassPathResource("scenes/ecommerce/ecommerce-skulist/SKILL.md"), "ecommerce"));
        skills.register(Skills.parse(
                new ClassPathResource("scenes/xiaohongshu/xhs-topiclist/SKILL.md"), "xiaohongshu"));
        skills.register(Skills.parse(
                new ClassPathResource("scenes/xiaohongshu/xhs-note/SKILL.md"), "xiaohongshu"));
        skills.register(Skills.parse(
                new ClassPathResource("scenes/xiaohongshu/xhs-break/SKILL.md"), "xiaohongshu"));
        return skills;
    }

    private static Skill billedSkill(String id, String persistAs) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef("classpath:scenes/test/" + id + "/SKILL.md")
                .allowedTools(Collections.singletonList("read_skill"))
                .persistAs(persistAs)
                .build();
    }
}

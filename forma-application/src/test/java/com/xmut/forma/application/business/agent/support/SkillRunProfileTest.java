package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.agent.skill.SkillCatalogProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRunProfileTest {

    @Test
    void blankSkillIdResolvesToNoSkill() {
        SkillRunProfile profile = SkillRunProfile.resolve(catalog(), null, false);
        assertFalse(profile.isSkillBound());
        assertTrue(profile.isSettleEnabled());
        assertFalse(profile.isDryRun());
        assertNull(profile.getSkillId());
        assertEquals(SkillRunProfile.PERSIST_NONE, profile.getPersistAs());
        assertTrue(profile.isRequireUserText());
    }

    @Test
    void dryRunStillBindsDefaultSkill() {
        SkillRunProfile profile = SkillRunProfile.resolve(catalog(), null, true);
        assertTrue(profile.isDryRun());
        assertTrue(profile.isSkillBound());
        assertEquals("ecommerce-picklist", profile.getSkillId());
    }

    @Test
    void skulistResolvesToBilledListing() {
        SkillRunProfile profile = SkillRunProfile.resolve(
                catalog(), SceneCapabilityPackLoader.SKILL_SKULIST, false);
        assertTrue(profile.isBilledSku());
        assertFalse(profile.isBilledPicklist());
        assertEquals(SkillRunProfile.PERSIST_SKU, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_SKULIST, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isRequireUserText());
    }

    @Test
    void billedListingFactoryMatchesResolve() {
        SkillRunProfile profile = SkillRunProfile.billedListing();
        assertEquals(SkillRunProfile.PERSIST_SKU, profile.getPersistAs());
        assertTrue(profile.isBilledSku());
    }

    @Test
    void unknownSkillStillRejected() {
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
    void xhsTopiclistResolvesToPersistXhsTopiclist() {
        SkillRunProfile profile = SkillRunProfile.resolve(
                catalog(), SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_TOPICLIST, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isRequireUserText());
        assertTrue(profile.isBilledXhsTopiclist());
        assertEquals(SkillRunProfile.billedXhsTopiclist().getPersistAs(), profile.getPersistAs());
    }

    @Test
    void xhsNoteResolvesToPersistXhsNote() {
        SkillRunProfile profile = SkillRunProfile.resolve(
                catalog(), SceneCapabilityPackLoader.SKILL_XHS_NOTE, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_NOTE, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_NOTE, profile.getSkillId());
        assertTrue(profile.isBilledXhsNote());
        assertEquals(SkillRunProfile.billedXhsNote().getPersistAs(), profile.getPersistAs());
    }

    @Test
    void xhsBreakResolvesToPersistXhsBreak() {
        SkillRunProfile profile = SkillRunProfile.resolve(
                catalog(), SceneCapabilityPackLoader.SKILL_XHS_BREAK, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_BREAK, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_BREAK, profile.getSkillId());
        assertTrue(profile.isBilledXhsBreak());
        assertEquals(SkillRunProfile.billedXhsBreak().getPersistAs(), profile.getPersistAs());
    }

    @Test
    void techDigestResolvesToPersistTechDigest() {
        SkillRunProfile profile = SkillRunProfile.resolve(catalog(), "tech-digest", false);
        assertEquals("tech_digest", profile.getPersistAs());
        assertEquals(SkillRunProfile.PERSIST_TECH_DIGEST, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_TECH_DIGEST, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isRequireUserText());
        assertTrue(profile.isBilledTechDigest());
        assertEquals(SkillRunProfile.billedTechDigest().getPersistAs(), profile.getPersistAs());
    }

    private static SkillCatalog catalog() {
        InMemorySkillCatalog skills = new InMemorySkillCatalog(SkillCatalogProperties.allowMutation());
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_PICKLIST, SkillRunProfile.PERSIST_PICKLIST));
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_SKULIST, SkillRunProfile.PERSIST_SKU));
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, SkillRunProfile.PERSIST_XHS_TOPICLIST));
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_XHS_NOTE, SkillRunProfile.PERSIST_XHS_NOTE));
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_XHS_BREAK, SkillRunProfile.PERSIST_XHS_BREAK));
        skills.register(billedSkill(SceneCapabilityPackLoader.SKILL_TECH_DIGEST, SkillRunProfile.PERSIST_TECH_DIGEST));
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

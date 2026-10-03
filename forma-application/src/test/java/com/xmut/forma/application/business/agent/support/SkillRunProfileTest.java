package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.forma.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRunProfileTest {

    @Test
    void blankSkillIdResolvesToNoSkill() {
        SkillRunProfile profile = SkillRunProfile.resolve(null, false);
        assertFalse(profile.isSkillBound());
        assertTrue(profile.isSettleEnabled());
        assertFalse(profile.isDryRun());
        assertNull(profile.getSkillId());
        assertEquals(SkillRunProfile.PERSIST_NONE, profile.getPersistAs());
        assertTrue(profile.isRequireUserText());
    }

    @Test
    void dryRunStillBindsDefaultSkill() {
        SkillRunProfile profile = SkillRunProfile.resolve(null, true);
        assertTrue(profile.isDryRun());
        assertTrue(profile.isSkillBound());
        assertEquals("ecommerce-picklist", profile.getSkillId());
    }

    @Test
    void skulistResolvesToBilledListing() {
        SkillRunProfile profile = SkillRunProfile.resolve(SceneCapabilityPackLoader.SKILL_SKULIST, false);
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
        assertThrows(BusinessException.class, () -> SkillRunProfile.resolve("unknown-skill", false));
    }

    @Test
    void xhsTopiclistResolvesToPersistXhsTopiclist() {
        SkillRunProfile profile = SkillRunProfile.resolve(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_TOPICLIST, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_TOPICLIST, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isRequireUserText());
        assertTrue(profile.isBilledXhsTopiclist());
        assertEquals(SkillRunProfile.billedXhsTopiclist().getPersistAs(), profile.getPersistAs());
    }

    @Test
    void xhsNoteResolvesToPersistXhsNote() {
        SkillRunProfile profile = SkillRunProfile.resolve(SceneCapabilityPackLoader.SKILL_XHS_NOTE, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_NOTE, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_NOTE, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isBilledXhsNote());
        assertEquals(SkillRunProfile.billedXhsNote().getPersistAs(), profile.getPersistAs());
    }

    @Test
    void xhsBreakResolvesToPersistXhsBreak() {
        SkillRunProfile profile = SkillRunProfile.resolve(SceneCapabilityPackLoader.SKILL_XHS_BREAK, false);
        assertEquals(SkillRunProfile.PERSIST_XHS_BREAK, profile.getPersistAs());
        assertEquals(SceneCapabilityPackLoader.SKILL_XHS_BREAK, profile.getSkillId());
        assertTrue(profile.isSettleEnabled());
        assertTrue(profile.isBilledXhsBreak());
        assertEquals(SkillRunProfile.billedXhsBreak().getPersistAs(), profile.getPersistAs());
    }
}

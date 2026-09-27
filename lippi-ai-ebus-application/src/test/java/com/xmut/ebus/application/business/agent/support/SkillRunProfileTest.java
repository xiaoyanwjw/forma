package com.xmut.ebus.application.business.agent.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRunProfileTest {

    @Test
    void blankSkillIdResolvesToNoSkill() {
        SkillRunProfile profile = SkillRunProfile.resolve(null, false);
        assertFalse(profile.isSkillBound());
        assertFalse(profile.isSettleEnabled());
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
}

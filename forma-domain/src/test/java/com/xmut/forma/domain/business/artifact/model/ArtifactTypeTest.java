package com.xmut.forma.domain.business.artifact.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactTypeTest {

    @Test
    void fromCode_internsAndNormalizes() {
        assertEquals("picklist", ArtifactType.fromCode("picklist").getCode());
        assertSame(ArtifactType.fromCode("picklist"), ArtifactType.fromCode("PICKLIST"));
        assertEquals("sku", ArtifactType.fromCode("SKU").getCode());
    }

    @Test
    void onlyChatIsPlatformInternal() {
        assertFalse(ArtifactType.fromCode("chat").isSessionHistory());
        assertTrue(ArtifactType.fromCode("listing_plan").isSessionHistory());
        assertTrue(ArtifactType.fromCode("tech_digest").isSessionHistory());
        assertEquals(1, ArtifactType.internalCodes().size());
        assertTrue(ArtifactType.internalCodes().contains("chat"));
        assertFalse(ArtifactType.internalCodes().contains("listing_plan"));
    }
}

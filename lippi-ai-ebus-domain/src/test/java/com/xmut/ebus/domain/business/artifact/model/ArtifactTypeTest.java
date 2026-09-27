package com.xmut.ebus.domain.business.artifact.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactTypeTest {

    @Test
    void codes_are_picklist_and_sku() {
        assertEquals("picklist", ArtifactType.PICKLIST.getCode());
        assertEquals("sku", ArtifactType.SKU.getCode());
        assertEquals(ArtifactType.PICKLIST, ArtifactType.fromCode("picklist"));
        assertEquals(ArtifactType.SKU, ArtifactType.fromCode("SKU"));
    }

    @Test
    void codes_include_chat() {
        assertEquals("chat", ArtifactType.CHAT.getCode());
        assertEquals(ArtifactType.CHAT, ArtifactType.fromCode("chat"));
    }
}

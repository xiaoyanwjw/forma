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

    @Test
    void codes_include_listing_plan() {
        assertEquals("listing_plan", ArtifactType.LISTING_PLAN.getCode());
        assertEquals(ArtifactType.LISTING_PLAN, ArtifactType.fromCode("listing_plan"));
    }

    @Test
    void codes_include_xhs_types() {
        assertEquals("xhs_topiclist", ArtifactType.XHS_TOPICLIST.getCode());
        assertEquals("xhs_note", ArtifactType.XHS_NOTE.getCode());
        assertEquals("xhs_break", ArtifactType.XHS_BREAK.getCode());
        assertEquals(ArtifactType.XHS_TOPICLIST, ArtifactType.fromCode("xhs_topiclist"));
        assertEquals(ArtifactType.XHS_NOTE, ArtifactType.fromCode("XHS_NOTE"));
        assertEquals(ArtifactType.XHS_BREAK, ArtifactType.fromCode("xhs_break"));
    }
}

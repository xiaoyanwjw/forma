package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTurnAttachmentProviderTest {

    @Test
    void packs_catalog_output() {
        CatalogTurnAttachmentProvider provider = providerWith(oneShot());

        TurnAttachment attachment = provider.of("ecommerce-skulist", null);

        assertEquals("view.json", attachment.get(TurnDeliverableKeys.OUTPUT));
        assertEquals("view.json", TurnDeliverableKeys.output(attachment));
    }

    @Test
    void ignores_resume_option() {
        CatalogTurnAttachmentProvider provider = providerWith(Skill.builder()
                .id("ecommerce-skulist")
                .description("skulist")
                .promptRef("classpath:skulist.md")
                .output("out/view.json")
                .build());

        TurnAttachment attachment = provider.of("ecommerce-skulist", "confirm_execute");

        assertEquals("out/view.json", TurnDeliverableKeys.output(attachment));
    }

    @Test
    void null_skill_returns_empty() {
        CatalogTurnAttachmentProvider provider = providerWith(oneShot());

        TurnAttachment attachment = provider.of(null, null);

        assertTrue(attachment.isEmpty());
        assertNull(TurnDeliverableKeys.output(attachment));
        assertEquals(TurnAttachment.empty(), attachment);
    }

    @Test
    void incomplete_or_unsafe_slot_returns_null() {
        assertNull(TurnReminder.slot("../view.json"));
        assertNull(TurnReminder.slot("/tmp/a.json"));
    }

    private static CatalogTurnAttachmentProvider providerWith(Skill skill) {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(skill);
        return new CatalogTurnAttachmentProvider(catalog);
    }

    private static Skill oneShot() {
        return Skill.builder()
                .id("ecommerce-skulist")
                .description("skulist")
                .promptRef("classpath:skulist.md")
                .output("view.json")
                .build();
    }
}

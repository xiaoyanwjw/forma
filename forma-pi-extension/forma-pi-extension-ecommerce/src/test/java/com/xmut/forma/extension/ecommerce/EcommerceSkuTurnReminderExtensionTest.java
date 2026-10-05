package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.extension.config.SkuToolsConfiguration;
import com.xmut.forma.extension.output.DefaultUserModifier;
import com.xmut.forma.extension.output.TurnReminder;
import com.xmut.forma.extension.output.TurnReminderExtension;
import com.xmut.forma.pi.agent.agent.UserPromptInput;
import com.xmut.forma.pi.agent.event.DefaultPiEventBus;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcommerceSkuTurnReminderExtensionTest {

    private static final String SKILL = "ecommerce-skulist";

    @Test
    void leaves_user_unset_for_other_skills_even_when_text_confirms() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        assertUserUnset(extension, request("ecommerce-picklist", "confirm_execute"));
        assertUserUnset(extension, request("other", "hello"));
        assertUserUnset(extension, null);
        assertUserUnset(extension, PiEvent.of(PiEventType.BEFORE_AGENT_START));
        assertUserUnset(extension, PiEvent.of(PiEventType.COMMAND, "nope"));
    }

    @Test
    void leaves_user_unset_when_skill_id_blank_without_confirm() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        assertUserUnset(extension, request(null, "hello"));
        assertUserUnset(extension, request("  ", "supplement the frames"));
    }

    @Test
    void plan_slots_when_skulist_and_not_confirm() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        ContextModifier modifier = apply(extension, request(SKILL, "supplement the frames"));

        assertUserPrefix(modifier, "supplement the frames", "plan/view.json", "plan/artifact.json");
        assertTrue(modifier.getUser() instanceof EcommerceSkuUserModifier);
    }

    @Test
    void exec_slots_when_skulist_confirms() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        ContextModifier modifier = apply(extension, request(SKILL, "{\"selectedId\":\"confirm_execute\"}"));

        assertUserPrefix(modifier, "{\"selectedId\":\"confirm_execute\"}", "exec/view.json", "exec/artifact.json");
    }

    @Test
    void exec_slots_when_skill_id_blank_and_text_contains_confirm() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        ContextModifier modifier = apply(extension, request("  ", "go confirm_execute now"));

        assertUserPrefix(modifier, "go confirm_execute now", "exec/view.json", "exec/artifact.json");
    }

    @Test
    void leaves_user_unset_when_catalog_misses_skulist() {
        EcommerceSkuTurnReminderExtension extension = new EcommerceSkuTurnReminderExtension(new InMemorySkillCatalog());

        assertUserUnset(extension, request(SKILL, "hello"));
        assertUserUnset(extension, request(null, "confirm_execute"));
    }

    @Test
    void leaves_user_unset_when_required_slots_missing_or_unsafe() {
        assertUserUnset(extension(skill("plan/view.json", null, "exec/view.json", "exec/artifact.json")),
                request(SKILL, "hello"));
        assertUserUnset(extension(skill("plan/../view.json", "plan/artifact.json", "exec/view.json", "exec/artifact.json")),
                request(SKILL, "hello"));
        assertUserUnset(extension(skill("plan/view.json", "plan/artifact.json", "/exec/view.json", "exec/artifact.json")),
                request(SKILL, "confirm_execute"));
        assertUserUnset(extension(skill("plan/view.json", "plan/artifact.json", null, null)),
                request(null, "confirm_execute"));
    }

    @Test
    void apply_does_not_mutate_the_caller_message_list() {
        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.user("hello"));
        EcommerceSkuTurnReminderExtension extension = extension(slotted());

        ContextModifier modifier = apply(extension, request(SKILL, "hello"));

        assertNotNull(modifier.getUser());
        List<Message> out = modifier.getUser().apply(messages);
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getContent());
        assertEquals(TurnReminder.prefix("plan/view.json", "plan/artifact.json") + "hello", out.get(0).getContent());
        assertNull(modifier.getUser().apply(null));
    }

    @Test
    void when_both_set_user_bus_keeps_only_the_first() {
        Skill skill = skill(null, null, "exec/view.json", "exec/artifact.json");
        SkillCatalog catalog = catalog(skill);
        EcommerceSkuTurnReminderExtension sku = new EcommerceSkuTurnReminderExtension(catalog);
        TurnReminderExtension core = new TurnReminderExtension(catalog);
        PiEvent event = request(SKILL, "confirm_execute");

        ContextModifier skuOnly = apply(sku, event);
        ContextModifier coreOnly = ContextModifier.empty();
        core.onBeforeAgentStart(coreOnly, event);
        assertTrue(skuOnly.getUser() instanceof EcommerceSkuUserModifier);
        assertTrue(coreOnly.getUser() instanceof DefaultUserModifier);

        DefaultPiEventBus skuFirst = new DefaultPiEventBus();
        skuFirst.register(PiEventType.BEFORE_AGENT_START, sku::onBeforeAgentStart);
        skuFirst.register(PiEventType.BEFORE_AGENT_START, core::onBeforeAgentStart);
        ContextModifier skuWins = skuFirst.emit(event, ContextModifier.class);
        assertTrue(skuWins.getUser() instanceof EcommerceSkuUserModifier);
        assertUserPrefix(skuWins, "confirm_execute", "exec/view.json", "exec/artifact.json");

        DefaultPiEventBus coreFirst = new DefaultPiEventBus();
        coreFirst.register(PiEventType.BEFORE_AGENT_START, core::onBeforeAgentStart);
        coreFirst.register(PiEventType.BEFORE_AGENT_START, sku::onBeforeAgentStart);
        ContextModifier coreWins = coreFirst.emit(event, ContextModifier.class);
        assertTrue(coreWins.getUser() instanceof DefaultUserModifier);
        assertUserPrefix(coreWins, "confirm_execute", "exec/view.json", "exec/artifact.json");
    }

    @Test
    void plan_view_makes_core_leave_user_unset() {
        SkillCatalog catalog = catalog(slotted());
        EcommerceSkuTurnReminderExtension sku = new EcommerceSkuTurnReminderExtension(catalog);
        TurnReminderExtension core = new TurnReminderExtension(catalog);
        PiEvent event = request(SKILL, "write the plan");

        ContextModifier coreOnly = ContextModifier.empty();
        core.onBeforeAgentStart(coreOnly, event);
        assertNull(coreOnly.getUser());
        assertUserPrefix(apply(sku, event), "write the plan", "plan/view.json", "plan/artifact.json");

        DefaultPiEventBus coreFirst = new DefaultPiEventBus();
        coreFirst.register(PiEventType.BEFORE_AGENT_START, core::onBeforeAgentStart);
        coreFirst.register(PiEventType.BEFORE_AGENT_START, sku::onBeforeAgentStart);
        ContextModifier winner = coreFirst.emit(event, ContextModifier.class);
        assertTrue(winner.getUser() instanceof EcommerceSkuUserModifier);
        assertUserPrefix(winner, "write the plan", "plan/view.json", "plan/artifact.json");
    }

    @Test
    void register_puts_plan_modifier_on_the_bus() {
        EcommerceSkuTurnReminderExtension extension = extension(slotted());
        DefaultPiEventBus bus = new DefaultPiEventBus();
        extension.register(bus);

        ContextModifier modifier = bus.emit(request(SKILL, "hello"), ContextModifier.class);

        assertUserPrefix(modifier, "hello", "plan/view.json", "plan/artifact.json");
    }

    @Test
    void register_rejects_null_bus() {
        assertThrows(IllegalArgumentException.class, () -> extension(slotted()).register(null));
    }

    @Test
    void configuration_bean() {
        assertNotNull(new SkuToolsConfiguration().ecommerceSkuTurnReminderExtension(new InMemorySkillCatalog()));
    }

    private static void assertUserUnset(EcommerceSkuTurnReminderExtension extension, PiEvent event) {
        assertNull(apply(extension, event).getUser());
    }

    private static ContextModifier apply(EcommerceSkuTurnReminderExtension extension, PiEvent event) {
        ContextModifier modifier = ContextModifier.empty();
        extension.onBeforeAgentStart(modifier, event);
        return modifier;
    }

    private static void assertUserPrefix(ContextModifier modifier, String body, String view, String artifact) {
        assertNotNull(modifier.getUser());
        List<Message> formatted = UserPromptInput.builder()
                .messages(Collections.singletonList(Message.user(body)))
                .apply(modifier)
                .build()
                .format();
        assertEquals(TurnReminder.prefix(view, artifact) + body, formatted.get(0).getContent());
    }

    private static EcommerceSkuTurnReminderExtension extension(Skill skill) {
        return new EcommerceSkuTurnReminderExtension(catalog(skill));
    }

    private static SkillCatalog catalog(Skill skill) {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(skill);
        return catalog;
    }

    private static Skill slotted() {
        return skill("plan/view.json", "plan/artifact.json", "exec/view.json", "exec/artifact.json");
    }

    private static Skill skill(String planView, String planArtifact, String view, String artifact) {
        return Skill.builder()
                .id(SKILL)
                .description("listing")
                .promptRef("classpath:scenes/ecommerce/ecommerce-skulist/SKILL.md")
                .viewPath(view)
                .artifactPath(artifact)
                .planViewPath(planView)
                .planArtifactPath(planArtifact)
                .build();
    }

    private static PiEvent request(String skillId, String userText) {
        return PiEvent.of(PiEventType.BEFORE_AGENT_START, BeforeAgentStartEvent.builder()
                .runId("run-1")
                .skillId(skillId)
                .workspaceRoot("/tmp/ws")
                .userText(userText)
                .build());
    }
}

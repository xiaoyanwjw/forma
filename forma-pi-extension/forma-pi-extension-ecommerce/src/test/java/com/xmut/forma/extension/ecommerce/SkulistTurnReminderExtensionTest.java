package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.extension.config.SkuToolsConfiguration;
import com.xmut.forma.extension.output.TurnReminder;
import com.xmut.forma.extension.output.TurnReminderExtension;
import com.xmut.forma.pi.agent.event.DefaultPiEventBus;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeModelRequestEvent;
import com.xmut.forma.pi.agent.extension.ModelRequestModifier;
import com.xmut.forma.pi.agent.skill.InMemorySkillCatalog;
import com.xmut.forma.pi.agent.skill.Skill;
import com.xmut.forma.pi.agent.skill.SkillCatalog;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkulistTurnReminderExtensionTest {

    private static final String SKILL = "ecommerce-skulist";

    @Test
    void returns_null_for_other_skills_even_when_text_confirms() {
        SkulistTurnReminderExtension extension = extension(slotted());

        assertNull(extension.onBeforeModelRequest(request("ecommerce-picklist", "confirm_execute")));
        assertNull(extension.onBeforeModelRequest(request("other", "hello")));
        assertNull(extension.onBeforeModelRequest(null));
        assertNull(extension.onBeforeModelRequest(PiEvent.of(PiEventType.COMMAND, "nope")));
    }

    @Test
    void returns_null_when_skill_id_blank_without_confirm() {
        SkulistTurnReminderExtension extension = extension(slotted());

        assertNull(extension.onBeforeModelRequest(request(null, "hello")));
        assertNull(extension.onBeforeModelRequest(request("  ", "supplement the frames")));
    }

    @Test
    void plan_slots_when_skulist_and_not_confirm() {
        SkulistTurnReminderExtension extension = extension(slotted());

        ModelRequestModifier modifier = extension.onBeforeModelRequest(request(SKILL, "supplement the frames"));

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("plan/view.json", "plan/artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void exec_slots_when_skulist_confirms() {
        SkulistTurnReminderExtension extension = extension(slotted());

        ModelRequestModifier modifier = extension.onBeforeModelRequest(
                request(SKILL, "{\"selectedId\":\"confirm_execute\"}"));

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("exec/view.json", "exec/artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void exec_slots_when_skill_id_blank_and_text_contains_confirm() {
        SkulistTurnReminderExtension extension = extension(slotted());

        ModelRequestModifier modifier = extension.onBeforeModelRequest(
                request("  ", "go confirm_execute now"));

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("exec/view.json", "exec/artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void returns_null_when_catalog_misses_skulist() {
        SkulistTurnReminderExtension extension = new SkulistTurnReminderExtension(new InMemorySkillCatalog());

        assertNull(extension.onBeforeModelRequest(request(SKILL, "hello")));
        assertNull(extension.onBeforeModelRequest(request(null, "confirm_execute")));
    }

    @Test
    void returns_null_when_required_slots_missing_or_unsafe() {
        assertNull(extension(skill("plan/view.json", null, "exec/view.json", "exec/artifact.json"))
                .onBeforeModelRequest(request(SKILL, "hello")));
        assertNull(extension(skill("plan/../view.json", "plan/artifact.json", "exec/view.json", "exec/artifact.json"))
                .onBeforeModelRequest(request(SKILL, "hello")));
        assertNull(extension(skill("plan/view.json", "plan/artifact.json", "/exec/view.json", "exec/artifact.json"))
                .onBeforeModelRequest(request(SKILL, "confirm_execute")));
        assertNull(extension(skill("plan/view.json", "plan/artifact.json", null, null))
                .onBeforeModelRequest(request(null, "confirm_execute")));
    }

    @Test
    void does_not_mutate_messages() {
        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.user("hello"));
        BeforeModelRequestEvent payload = new BeforeModelRequestEvent(
                "run-1", SKILL, "/tmp/ws", "hello", messages);
        SkulistTurnReminderExtension extension = extension(slotted());

        assertNotNull(extension.onBeforeModelRequest(PiEvent.of(PiEventType.BEFORE_MODEL_REQUEST, payload)));
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getContent());
        assertSame(messages, payload.getMessages());
    }

    @Test
    void when_both_non_null_bus_keeps_only_the_first() {
        Skill skill = skill(null, null, "exec/view.json", "exec/artifact.json");
        SkillCatalog catalog = catalog(skill);
        SkulistTurnReminderExtension skulist = new SkulistTurnReminderExtension(catalog);
        TurnReminderExtension core = new TurnReminderExtension(catalog);
        PiEvent event = request(SKILL, "confirm_execute");

        assertNotNull(skulist.onBeforeModelRequest(event));
        assertNotNull(core.onBeforeModelRequest(event));

        AtomicInteger coreCalls = new AtomicInteger();
        DefaultPiEventBus bus = new DefaultPiEventBus();
        bus.register(PiEventType.BEFORE_MODEL_REQUEST, skulist::onBeforeModelRequest);
        bus.register(PiEventType.BEFORE_MODEL_REQUEST, e -> {
            coreCalls.incrementAndGet();
            return core.onBeforeModelRequest(e);
        });

        ModelRequestModifier winner = bus.emit(event, ModelRequestModifier.class);

        assertEquals(0, coreCalls.get());
        assertEquals(TurnReminder.prefix("exec/view.json", "exec/artifact.json"), winner.getLastUserPrefix());
    }

    @Test
    void plan_view_makes_core_return_null() {
        SkillCatalog catalog = catalog(slotted());
        SkulistTurnReminderExtension skulist = new SkulistTurnReminderExtension(catalog);
        TurnReminderExtension core = new TurnReminderExtension(catalog);
        PiEvent event = request(SKILL, "write the plan");

        assertNull(core.onBeforeModelRequest(event));
        ModelRequestModifier plan = skulist.onBeforeModelRequest(event);
        assertEquals(TurnReminder.prefix("plan/view.json", "plan/artifact.json"), plan.getLastUserPrefix());

        DefaultPiEventBus coreFirst = new DefaultPiEventBus();
        coreFirst.register(PiEventType.BEFORE_MODEL_REQUEST, core::onBeforeModelRequest);
        coreFirst.register(PiEventType.BEFORE_MODEL_REQUEST, skulist::onBeforeModelRequest);
        ModelRequestModifier winner = coreFirst.emit(event, ModelRequestModifier.class);
        assertEquals(plan.getLastUserPrefix(), winner.getLastUserPrefix());
    }

    @Test
    void register_puts_plan_modifier_on_the_bus() {
        SkulistTurnReminderExtension extension = extension(slotted());
        DefaultPiEventBus bus = new DefaultPiEventBus();
        extension.register(bus);

        ModelRequestModifier modifier = bus.emit(request(SKILL, "hello"), ModelRequestModifier.class);

        assertEquals(TurnReminder.prefix("plan/view.json", "plan/artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void register_rejects_null_bus() {
        assertThrows(IllegalArgumentException.class, () -> extension(slotted()).register(null));
    }

    @Test
    void configuration_bean() {
        assertNotNull(new SkuToolsConfiguration().skulistTurnReminderExtension(new InMemorySkillCatalog()));
    }

    private static SkulistTurnReminderExtension extension(Skill skill) {
        return new SkulistTurnReminderExtension(catalog(skill));
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

    private static PiEvent request(String skillId, String thisTurnText) {
        return PiEvent.of(PiEventType.BEFORE_MODEL_REQUEST, new BeforeModelRequestEvent(
                "run-1",
                skillId,
                "/tmp/ws",
                thisTurnText,
                Collections.singletonList(Message.user(thisTurnText == null ? "hi" : thisTurnText))));
    }
}

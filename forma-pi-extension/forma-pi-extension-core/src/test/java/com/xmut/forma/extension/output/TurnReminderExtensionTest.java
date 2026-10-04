package com.xmut.forma.extension.output;

import com.xmut.forma.extension.config.ViewToolsConfiguration;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnReminderExtensionTest {

    @Test
    void returns_null_when_event_is_not_ours() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        assertNull(extension.onBeforeModelRequest(null));
        assertNull(extension.onBeforeModelRequest(PiEvent.of(PiEventType.BEFORE_MODEL_REQUEST)));
        assertNull(extension.onBeforeModelRequest(PiEvent.of(PiEventType.COMMAND, "nope")));
    }

    @Test
    void returns_null_when_skill_id_blank() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        assertNull(extension.onBeforeModelRequest(request(null, "hello")));
        assertNull(extension.onBeforeModelRequest(request("  ", "hello")));
    }

    @Test
    void returns_null_when_skill_missing() {
        TurnReminderExtension extension = new TurnReminderExtension(new InMemorySkillCatalog());

        assertNull(extension.onBeforeModelRequest(request("demo", "hello")));
    }

    @Test
    void returns_null_when_view_or_artifact_missing() {
        assertNull(extension(skill("demo", null, "artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
        assertNull(extension(skill("demo", "  ", "artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
        assertNull(extension(skill("demo", "view.json", null, null, null))
                .onBeforeModelRequest(request("demo", "hello")));
    }

    @Test
    void returns_null_when_plan_view_path_present() {
        TurnReminderExtension extension = extension(
                skill("ecommerce-skulist", "exec/view.json", "exec/artifact.json",
                        "plan/view.json", "plan/artifact.json"));

        assertNull(extension.onBeforeModelRequest(request("ecommerce-skulist", "hello")));
    }

    @Test
    void plan_artifact_alone_does_not_block() {
        TurnReminderExtension extension = extension(
                skill("demo", "view.json", "artifact.json", null, "plan/artifact.json"));

        ModelRequestModifier modifier = extension.onBeforeModelRequest(request("demo", "hello"));

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("view.json", "artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void returns_null_when_path_has_dotdot_or_is_absolute() {
        assertNull(extension(skill("demo", "plan/../view.json", "artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
        assertNull(extension(skill("demo", "view.json", "/tmp/artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
        assertNull(extension(skill("demo", "C:/view.json", "artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
        assertNull(extension(skill("demo", "\\\\share\\view.json", "artifact.json", null, null))
                .onBeforeModelRequest(request("demo", "hello")));
    }

    @Test
    void returns_modifier_for_relative_slots() {
        TurnReminderExtension extension = extension(
                skill("demo", " view.json ", " artifact.json ", "  ", null));

        ModelRequestModifier modifier = extension.onBeforeModelRequest(request(" demo ", "hello"));

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("view.json", "artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void does_not_mutate_messages_and_allows_null_message_list() {
        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.user("hi"));
        BeforeModelRequestEvent payload = new BeforeModelRequestEvent(
                "run-1", "demo", "/tmp/ws", "hi", messages);
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        ModelRequestModifier modifier = extension.onBeforeModelRequest(
                PiEvent.of(PiEventType.BEFORE_MODEL_REQUEST, payload));

        assertNotNull(modifier);
        assertEquals(1, messages.size());
        assertEquals("hi", messages.get(0).getContent());
        assertSame(messages, payload.getMessages());

        assertNotNull(extension.onBeforeModelRequest(PiEvent.of(
                PiEventType.BEFORE_MODEL_REQUEST,
                new BeforeModelRequestEvent("run-1", "demo", "/tmp/ws", "hi", null))));
    }

    @Test
    void register_puts_modifier_on_the_bus() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));
        DefaultPiEventBus bus = new DefaultPiEventBus();
        extension.register(bus);

        ModelRequestModifier modifier = bus.emit(request("demo", "hello"), ModelRequestModifier.class);

        assertNotNull(modifier);
        assertEquals(TurnReminder.prefix("view.json", "artifact.json"), modifier.getLastUserPrefix());
    }

    @Test
    void register_rejects_null_bus() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));
        assertThrows(IllegalArgumentException.class, () -> extension.register(null));
    }

    @Test
    void configuration_bean() {
        SkillCatalog catalog = new InMemorySkillCatalog();
        TurnReminderExtension bean = new ViewToolsConfiguration().turnReminderExtension(catalog);
        assertNotNull(bean);
    }

    private static TurnReminderExtension extension(Skill skill) {
        InMemorySkillCatalog catalog = new InMemorySkillCatalog();
        catalog.registerBootstrap(skill);
        return new TurnReminderExtension(catalog);
    }

    private static Skill skill(String id, String view, String artifact, String planView, String planArtifact) {
        return Skill.builder()
                .id(id)
                .description(id)
                .promptRef("classpath:" + id + ".md")
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

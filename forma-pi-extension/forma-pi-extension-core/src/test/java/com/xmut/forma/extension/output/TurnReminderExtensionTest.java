package com.xmut.forma.extension.output;

import com.xmut.forma.extension.config.ViewToolsConfiguration;
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

class TurnReminderExtensionTest {

    @Test
    void leaves_user_unset_when_event_is_not_ours() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        assertUserUnset(extension, null);
        assertUserUnset(extension, PiEvent.of(PiEventType.BEFORE_AGENT_START));
        assertUserUnset(extension, PiEvent.of(PiEventType.COMMAND, "nope"));
    }

    @Test
    void leaves_user_unset_when_skill_id_blank() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        assertUserUnset(extension, request(null, "hello"));
        assertUserUnset(extension, request("  ", "hello"));
    }

    @Test
    void leaves_user_unset_when_skill_missing() {
        TurnReminderExtension extension = new TurnReminderExtension(new InMemorySkillCatalog());

        assertUserUnset(extension, request("demo", "hello"));
    }

    @Test
    void leaves_user_unset_when_view_or_artifact_missing() {
        assertUserUnset(extension(skill("demo", null, "artifact.json", null, null)), request("demo", "hello"));
        assertUserUnset(extension(skill("demo", "  ", "artifact.json", null, null)), request("demo", "hello"));
        assertUserUnset(extension(skill("demo", "view.json", null, null, null)), request("demo", "hello"));
    }

    @Test
    void leaves_user_unset_when_plan_view_path_present() {
        TurnReminderExtension extension = extension(
                skill("ecommerce-skulist", "exec/view.json", "exec/artifact.json",
                        "plan/view.json", "plan/artifact.json"));

        assertUserUnset(extension, request("ecommerce-skulist", "hello"));
    }

    @Test
    void plan_artifact_alone_does_not_block() {
        TurnReminderExtension extension = extension(
                skill("demo", "view.json", "artifact.json", null, "plan/artifact.json"));

        ContextModifier modifier = apply(extension, request("demo", "hello"));

        assertUserPrefix(modifier, "hello", "view.json", "artifact.json");
    }

    @Test
    void leaves_user_unset_when_path_has_dotdot_or_is_absolute() {
        assertUserUnset(extension(skill("demo", "plan/../view.json", "artifact.json", null, null)),
                request("demo", "hello"));
        assertUserUnset(extension(skill("demo", "view.json", "/tmp/artifact.json", null, null)),
                request("demo", "hello"));
        assertUserUnset(extension(skill("demo", "C:/view.json", "artifact.json", null, null)),
                request("demo", "hello"));
        assertUserUnset(extension(skill("demo", "\\\\share\\view.json", "artifact.json", null, null)),
                request("demo", "hello"));
    }

    @Test
    void sets_user_modifier_for_relative_slots() {
        TurnReminderExtension extension = extension(
                skill("demo", " view.json ", " artifact.json ", "  ", null));

        ContextModifier modifier = apply(extension, request(" demo ", "hello"));

        assertUserPrefix(modifier, "hello", "view.json", "artifact.json");
    }

    @Test
    void apply_does_not_mutate_the_caller_message_list() {
        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.user("hi"));
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));

        ContextModifier modifier = apply(extension, request("demo", "hi"));

        assertNotNull(modifier.getUser());
        List<Message> out = modifier.getUser().apply(messages);
        assertEquals(1, messages.size());
        assertEquals("hi", messages.get(0).getContent());
        assertEquals(TurnReminder.prefix("view.json", "artifact.json") + "hi", out.get(0).getContent());
        assertNull(modifier.getUser().apply(null));
    }

    @Test
    void register_puts_user_modifier_on_the_bus() {
        TurnReminderExtension extension = extension(skill("demo", "view.json", "artifact.json", null, null));
        DefaultPiEventBus bus = new DefaultPiEventBus();
        extension.register(bus);

        ContextModifier modifier = bus.emit(request("demo", "hello"), ContextModifier.class);

        assertUserPrefix(modifier, "hello", "view.json", "artifact.json");
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

    private static void assertUserUnset(TurnReminderExtension extension, PiEvent event) {
        assertNull(apply(extension, event).getUser());
    }

    private static ContextModifier apply(TurnReminderExtension extension, PiEvent event) {
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
        return PiEvent.of(PiEventType.BEFORE_AGENT_START, BeforeAgentStartEvent.builder()
                .runId("run-1")
                .skillId(skillId)
                .workspaceRoot("/tmp/ws")
                .userText(thisTurnText)
                .build());
    }
}

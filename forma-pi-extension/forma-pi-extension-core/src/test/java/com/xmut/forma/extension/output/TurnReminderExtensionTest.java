package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.extension.config.ViewToolsConfiguration;
import com.xmut.forma.pi.agent.agent.UserPromptInput;
import com.xmut.forma.pi.agent.event.DefaultPiEventBus;
import com.xmut.forma.pi.agent.event.PiEvent;
import com.xmut.forma.pi.agent.event.PiEventType;
import com.xmut.forma.pi.agent.extension.BeforeAgentStartEvent;
import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnReminderExtensionTest {

    @Test
    void leaves_user_unset_when_event_is_not_ours() {
        TurnReminderExtension extension = new TurnReminderExtension();

        assertUserUnset(extension, null);
        assertUserUnset(extension, PiEvent.of(PiEventType.BEFORE_AGENT_START));
        assertUserUnset(extension, PiEvent.of(PiEventType.COMMAND, "nope"));
    }

    @Test
    void leaves_user_unset_when_paths_missing() {
        TurnReminderExtension extension = new TurnReminderExtension();

        assertUserUnset(extension, request(null));
        assertUserUnset(extension, request("  "));
    }

    @Test
    void leaves_user_unset_when_path_has_dotdot_or_is_absolute() {
        TurnReminderExtension extension = new TurnReminderExtension();

        assertUserUnset(extension, request("plan/../view.json"));
        assertUserUnset(extension, request("/tmp/view.json"));
        assertUserUnset(extension, request("C:/view.json"));
        assertUserUnset(extension, request("\\\\share\\view.json"));
    }

    @Test
    void sets_user_modifier_for_event_slots() {
        TurnReminderExtension extension = new TurnReminderExtension();

        ContextModifier modifier = apply(extension, request(" plan/view.json "));

        assertUserOf(modifier, "hello", "plan/view.json");
    }

    @Test
    void apply_does_not_mutate_the_caller_message_list() {
        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.user("hi"));
        TurnReminderExtension extension = new TurnReminderExtension();

        ContextModifier modifier = apply(extension, request("view.json"));

        assertNotNull(modifier.getUser());
        List<Message> out = modifier.getUser().apply(messages);
        assertEquals(1, messages.size());
        assertEquals("hi", messages.get(0).getContent());
        assertEquals(TurnReminder.of("view.json") + "hi", out.get(0).getContent());
        assertNull(modifier.getUser().apply(null));
    }

    @Test
    void register_puts_user_modifier_on_the_bus() {
        TurnReminderExtension extension = new TurnReminderExtension();
        DefaultPiEventBus bus = new DefaultPiEventBus();
        extension.register(bus);

        ContextModifier modifier = bus.emit(request("view.json"), ContextModifier.class);

        assertUserOf(modifier, "hello", "view.json");
    }

    @Test
    void register_rejects_null_bus() {
        assertThrows(IllegalArgumentException.class, () -> new TurnReminderExtension().register(null));
    }

    @Test
    void configuration_bean() {
        assertNotNull(new ViewToolsConfiguration().turnReminderExtension());
    }

    private static void assertUserUnset(TurnReminderExtension extension, PiEvent event) {
        assertNull(apply(extension, event).getUser());
    }

    private static ContextModifier apply(TurnReminderExtension extension, PiEvent event) {
        ContextModifier modifier = ContextModifier.empty();
        extension.onBeforeAgentStart(modifier, event);
        return modifier;
    }

    private static void assertUserOf(ContextModifier modifier, String body, String output) {
        assertNotNull(modifier.getUser());
        List<Message> formatted = UserPromptInput.builder()
                .messages(Collections.singletonList(Message.user(body)))
                .apply(modifier)
                .build()
                .format();
        assertEquals(TurnReminder.of(output) + body, formatted.get(0).getContent());
    }

    private static PiEvent request(String output) {
        return PiEvent.of(PiEventType.BEFORE_AGENT_START, BeforeAgentStartEvent.builder()
                .runId("run-1")
                .skillId("demo")
                .workspaceRoot("/tmp/ws")
                .userText("hello")
                .attachment(attachmentOf(output))
                .build());
    }

    private static TurnAttachment attachmentOf(String output) {
        Map<String, Object> raw = new HashMap<String, Object>();
        if (output != null) {
            raw.put(TurnDeliverableKeys.OUTPUT, output);
        }
        return TurnAttachment.of(raw);
    }
}

package com.xmut.forma.extension.output;

import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultUserModifierTest {

    @Test
    void apply_delegates_to_prefix_last_user() {
        String prefix = TurnReminder.prefix("view.json", "artifact.json");
        List<Message> messages = Collections.singletonList(Message.user("hi"));

        List<Message> out = new DefaultUserModifier(prefix).apply(messages);

        assertEquals(prefix + "hi", out.get(0).getContent());
        assertEquals("hi", messages.get(0).getContent());
    }

    @Test
    void apply_blank_prefix_returns_the_same_list() {
        Message user = Message.user("hi");
        List<Message> messages = Collections.singletonList(user);

        assertSame(messages, new DefaultUserModifier(null).apply(messages));
        assertSame(messages, new DefaultUserModifier("  \n").apply(messages));
    }
}

package com.xmut.forma.extension.ecommerce;

import com.xmut.forma.extension.output.TurnReminder;
import com.xmut.forma.pi.ai.message.Message;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EcommerceSkuUserModifierTest {

    @Test
    void apply_prefixes_the_last_user() {
        String prefix = TurnReminder.prefix("exec/view.json", "exec/artifact.json");
        List<Message> messages = Collections.singletonList(Message.user("confirm_execute"));

        List<Message> out = new EcommerceSkuUserModifier(prefix).apply(messages);

        assertEquals(prefix + "confirm_execute", out.get(0).getContent());
        assertEquals("confirm_execute", messages.get(0).getContent());
    }

    @Test
    void apply_blank_prefix_returns_the_same_list() {
        Message user = Message.user("hi");
        List<Message> messages = Collections.singletonList(user);

        assertSame(messages, new EcommerceSkuUserModifier(null).apply(messages));
        assertSame(messages, new EcommerceSkuUserModifier("  \n").apply(messages));
    }
}

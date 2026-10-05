package com.xmut.forma.extension.output;

import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TurnReminderTest {

    private static final String PREFIX = "<reminder>\nX\n</reminder>\n\n";

    @Test
    void of_is_exact_bytes() {
        String prefix = TurnReminder.of("view.json");

        assertEquals(expected("view.json"), prefix);
        assertEquals('\n', prefix.charAt(prefix.length() - 1));
        assertEquals('\n', prefix.charAt(prefix.length() - 2));
    }

    @Test
    void of_interpolates_both_slots() {
        assertEquals(expected("plan/view.json"),
                TurnReminder.of("plan/view.json"));
        assertEquals(expected("out/view.json"),
                TurnReminder.of("out/view.json"));
    }

    @Test
    void strip_removes_leading_reminder_and_at_most_two_newlines() {
        String body = "用户原文";
        assertEquals(body, TurnReminder.strip(expected("view.json") + body));
        assertEquals(body, TurnReminder.strip("<reminder>\nX\n</reminder>\n" + body));
        assertEquals(body, TurnReminder.strip("<reminder>\nX\n</reminder>" + body));
    }

    @Test
    void strip_keeps_a_third_newline() {
        assertEquals("\nrest", TurnReminder.strip("<reminder>\nX\n</reminder>\n\n\nrest"));
    }

    @Test
    void strip_drops_leading_whitespace_then_the_block() {
        assertEquals("  hello", TurnReminder.strip(" \n<reminder>\nX\n</reminder>\n\n  hello"));
    }

    @Test
    void strip_leaves_text_that_does_not_start_with_the_tag() {
        String content = "note <reminder>\nX\n</reminder>\n\n";
        assertEquals(content, TurnReminder.strip(content));
        assertEquals("<REMINDER>\nX\n</reminder>\n\nrest",
                TurnReminder.strip("<REMINDER>\nX\n</reminder>\n\nrest"));
    }

    @Test
    void strip_stops_at_the_first_close_tag() {
        assertEquals(" early\n</reminder>\n\nrest",
                TurnReminder.strip("<reminder>\nsee </reminder> early\n</reminder>\n\nrest"));
    }

    @Test
    void strip_leaves_content_when_close_tag_is_missing() {
        String content = "<reminder>\nno close";
        assertEquals(content, TurnReminder.strip(content));
    }

    @Test
    void strip_null_and_empty() {
        assertNull(TurnReminder.strip(null));
        assertEquals("", TurnReminder.strip(""));
        assertEquals("   ", TurnReminder.strip("   "));
    }

    @Test
    void of_last_user_prefixes_only_the_last_user_and_does_not_mutate_input() {
        Message earlier = Message.user("earlier");
        Message assistant = Message.assistant("mid", Collections.<ToolCallEntry>emptyList());
        Message last = Message.user("原文");
        List<Message> input = new ArrayList<Message>(Arrays.asList(earlier, assistant, last));

        List<Message> out = TurnReminder.rewriteLastUser(input, PREFIX);

        assertSame(earlier, out.get(0));
        assertSame(assistant, out.get(1));
        assertEquals(PREFIX + "原文", out.get(2).getContent());
        assertEquals("原文", input.get(2).getContent());
        assertEquals("earlier", earlier.getContent());
    }

    @Test
    void of_last_user_prefixes_last_user_when_an_assistant_follows() {
        Message user = Message.user("ask");
        Message assistant = Message.assistant("ans", Collections.<ToolCallEntry>emptyList());
        List<Message> input = Arrays.asList(user, assistant);

        List<Message> out = TurnReminder.rewriteLastUser(input, "P\n");

        assertEquals("P\nask", out.get(0).getContent());
        assertSame(assistant, out.get(1));
        assertEquals("ask", user.getContent());
    }

    @Test
    void of_last_user_is_idempotent_when_content_already_starts_with_reminder() {
        String already = "  <reminder>\nold\n</reminder>\n\nbody";
        List<Message> input = new ArrayList<Message>(Collections.singletonList(Message.user(already)));

        List<Message> out = TurnReminder.rewriteLastUser(input, PREFIX);

        assertEquals(already, out.get(0).getContent());
        assertSame(input.get(0), out.get(0));
    }

    @Test
    void of_last_user_null_and_empty_return_as_is() {
        assertNull(TurnReminder.rewriteLastUser(null, PREFIX));
        List<Message> empty = Collections.emptyList();
        assertSame(empty, TurnReminder.rewriteLastUser(empty, PREFIX));
    }

    @Test
    void of_last_user_without_user_returns_input() {
        Message assistant = Message.assistant("only", Collections.<ToolCallEntry>emptyList());
        List<Message> input = Collections.singletonList(assistant);

        List<Message> out = TurnReminder.rewriteLastUser(input, PREFIX);

        assertSame(input, out);
        assertSame(assistant, out.get(0));
    }

    @Test
    void prefix_last_user_blank_of_returns_input() {
        Message user = Message.user("hi");
        List<Message> input = Collections.singletonList(user);

        assertSame(user, TurnReminder.rewriteLastUser(input, null).get(0));
        assertSame(user, TurnReminder.rewriteLastUser(input, "  \n").get(0));
    }

    private static String expected(String output) {
        return "<reminder>\n"
                + "本轮交付路径（相对本轮工作区；禁止改名；禁止复用上一轮路径）：\n"
                + "- output: " + output + "\n"
                + "必须由 render_view 写入。对话不要输出 {\"output\":...}。\n"
                + "</reminder>\n\n";
    }
}

package com.xmut.forma.common.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TurnReminderSyntaxTest {

    @Test
    void strip_removes_leading_reminder_and_at_most_two_newlines() {
        String body = "用户原文";
        assertEquals(body, TurnReminderSyntax.strip(prefix("view.json") + body));
        assertEquals(body, TurnReminderSyntax.strip("<reminder>\nX\n</reminder>\n" + body));
        assertEquals(body, TurnReminderSyntax.strip("<reminder>\nX\n</reminder>" + body));
    }

    @Test
    void strip_keeps_a_third_newline() {
        assertEquals("\nrest", TurnReminderSyntax.strip("<reminder>\nX\n</reminder>\n\n\nrest"));
    }

    @Test
    void strip_drops_leading_whitespace_then_the_block() {
        assertEquals("  hello", TurnReminderSyntax.strip(" \n<reminder>\nX\n</reminder>\n\n  hello"));
    }

    @Test
    void strip_leaves_text_that_does_not_start_with_the_tag() {
        String content = "note <reminder>\nX\n</reminder>\n\n";
        assertEquals(content, TurnReminderSyntax.strip(content));
        assertEquals("<REMINDER>\nX\n</reminder>\n\nrest",
                TurnReminderSyntax.strip("<REMINDER>\nX\n</reminder>\n\nrest"));
    }

    @Test
    void strip_stops_at_the_first_close_tag() {
        assertEquals(" early\n</reminder>\n\nrest",
                TurnReminderSyntax.strip("<reminder>\nsee </reminder> early\n</reminder>\n\nrest"));
    }

    @Test
    void strip_leaves_content_when_close_tag_is_missing() {
        String content = "<reminder>\nno close";
        assertEquals(content, TurnReminderSyntax.strip(content));
    }

    @Test
    void strip_null_and_empty() {
        assertNull(TurnReminderSyntax.strip(null));
        assertEquals("", TurnReminderSyntax.strip(""));
        assertEquals("   ", TurnReminderSyntax.strip("   "));
    }

    private static String prefix(String output) {
        return "<reminder>\n"
                + "本轮交付路径（相对本轮工作区；禁止改名；禁止复用上一轮路径）：\n"
                + "- output: " + output + "\n"
                + "必须由 render_view 写入。对话不要输出 {\"output\":...}。\n"
                + "</reminder>\n\n";
    }
}

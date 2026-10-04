package com.xmut.forma.extension.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TurnReminderTest {

    @Test
    void prefix_is_exact_bytes() {
        String prefix = TurnReminder.prefix("view.json", "artifact.json");

        assertEquals(expected("view.json", "artifact.json"), prefix);
        assertEquals('\n', prefix.charAt(prefix.length() - 1));
        assertEquals('\n', prefix.charAt(prefix.length() - 2));
    }

    @Test
    void prefix_interpolates_both_slots() {
        assertEquals(expected("plan/view.json", "plan/artifact.json"),
                TurnReminder.prefix("plan/view.json", "plan/artifact.json"));
        assertEquals(expected("exec/view.json", "exec/artifact.json"),
                TurnReminder.prefix("exec/view.json", "exec/artifact.json"));
    }

    @Test
    void strip_removes_leading_reminder_and_at_most_two_newlines() {
        String body = "用户原文";
        assertEquals(body, TurnReminder.strip(expected("view.json", "artifact.json") + body));
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

    private static String expected(String viewPath, String artifactPath) {
        return "<reminder>\n"
                + "本轮交付槽位（相对本轮工作区；禁止改名；禁止复用上一轮路径）：\n"
                + "- view: " + viewPath + "\n"
                + "- artifact: " + artifactPath + "\n"
                + "必须由 write_file / render_view 写入。对话不要输出 {\"output\":...}。\n"
                + "</reminder>\n\n";
    }
}

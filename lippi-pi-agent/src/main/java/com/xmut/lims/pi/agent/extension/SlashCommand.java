package com.xmut.lims.pi.agent.extension;

import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * 已登记斜杠命令（Story 51-14）。{@code name} 无前导 {@code /}。
 */
@Value
public class SlashCommand {

    String name;
    String args;
    String raw;

    public static SlashCommand parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        int space = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                space = i;
                break;
            }
        }
        String name = space < 0 ? trimmed.substring(1) : trimmed.substring(1, space);
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String args = space < 0 ? "" : trimmed.substring(space).trim();
        return new SlashCommand(name, args, trimmed);
    }
}

package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnReminderSyntax;
import com.xmut.forma.common.util.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

/**
 * 本轮交付槽位提醒。
 * 功能描述：拼出贴在最后一条 user 前的固定 reminder，并能从 content 里剥掉它。
 * 关键设计：字节由测试锁死；路径是否能写进提醒由 {@link #slot(String)} 决定。
 */
public final class TurnReminder {

    private TurnReminder() {
    }

    public static String prefix(String viewPath, String artifactPath) {
        return "<reminder>\n"
                + "本轮交付槽位（相对本轮工作区；禁止改名；禁止复用上一轮路径）：\n"
                + "- view: " + viewPath + "\n"
                + "- artifact: " + artifactPath + "\n"
                + "必须由 write_file / render_view 写入。对话不要输出 {\"output\":...}。\n"
                + "</reminder>\n\n";
    }

    /**
     * 若 trim 后以 {@code <reminder>} 起，删到第一个 {@code </reminder>} 及其后至多两个换行。
     * 规则在 {@link TurnReminderSyntax#strip(String)}，与会话标题、回放共用。
     */
    public static String strip(String content) {
        return TurnReminderSyntax.strip(content);
    }

    /**
     * 可写入提醒的相对路径。空白、含 {@code ..} 或绝对路径返回 null。
     */
    public static String slot(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String value = path.trim();
        if (value.contains("..")) {
            return null;
        }
        char first = value.charAt(0);
        if (first == '/' || first == '\\') {
            return null;
        }
        if (value.length() >= 2 && value.charAt(1) == ':' && Character.isLetter(value.charAt(0))) {
            return null;
        }
        try {
            if (Paths.get(value).isAbsolute()) {
                return null;
            }
        } catch (InvalidPathException ex) {
            return null;
        }
        return value;
    }
}

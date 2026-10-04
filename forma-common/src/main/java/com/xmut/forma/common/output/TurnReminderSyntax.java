package com.xmut.forma.common.output;

/**
 * 本轮 {@code <reminder>} 标签的字节规则。
 * 功能描述：从 user 文本剥掉开头的 reminder，供提示词与会话标题/回放共用。
 * 关键设计：只认小写标签；关标签后最多吃掉两个换行；不依赖提醒正文。
 */
public final class TurnReminderSyntax {

    private static final String OPEN = "<reminder>";
    private static final String CLOSE = "</reminder>";

    private TurnReminderSyntax() {
    }

    /**
     * 若 trim 后以 {@code <reminder>} 起，删到第一个 {@code </reminder>} 及其后至多两个换行。
     * 关标签缺失或不是开头时原样返回。{@code null} 返回 {@code null}。
     */
    public static String strip(String content) {
        if (content == null) {
            return null;
        }
        if (!javaTrim(content).startsWith(OPEN)) {
            return content;
        }
        int open = leadingTrimEnd(content);
        int close = content.indexOf(CLOSE, open + OPEN.length());
        if (close < 0) {
            return content;
        }
        int end = close + CLOSE.length();
        int newlines = 0;
        while (end < content.length() && newlines < 2 && content.charAt(end) == '\n') {
            end++;
            newlines++;
        }
        return content.substring(end);
    }

    /** Same cut as {@link String#trim()}. */
    private static String javaTrim(String content) {
        int start = leadingTrimEnd(content);
        int end = content.length();
        while (end > start && content.charAt(end - 1) <= ' ') {
            end--;
        }
        return content.substring(start, end);
    }

    /** Chars {@code <= ' '}, matching {@link String#trim()}. */
    private static int leadingTrimEnd(String content) {
        int i = 0;
        while (i < content.length() && content.charAt(i) <= ' ') {
            i++;
        }
        return i;
    }
}

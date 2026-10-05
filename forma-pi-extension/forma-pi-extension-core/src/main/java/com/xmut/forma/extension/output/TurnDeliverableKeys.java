package com.xmut.forma.extension.output;

import com.xmut.forma.common.output.TurnAttachment;

/**
 * 本轮交付附件键。
 * 功能描述：从 TurnAttachment 取出 output 相对路径。
 * 关键设计：非 String 当缺失；路径合法性交给 {@link TurnReminder#slot(String)}。
 */
public final class TurnDeliverableKeys {

    public static final String OUTPUT = "output";

    private TurnDeliverableKeys() {
    }

    public static String output(TurnAttachment attachment) {
        TurnAttachment att = attachment == null ? TurnAttachment.empty() : attachment;
        Object value = att.get(OUTPUT);
        if (!(value instanceof String)) {
            return null;
        }
        return TurnReminder.slot((String) value);
    }
}

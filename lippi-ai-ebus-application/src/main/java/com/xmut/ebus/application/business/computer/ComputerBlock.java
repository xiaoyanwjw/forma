package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computer view block; serialized as plain maps for SSE / JSON.
 */
public final class ComputerBlock {

    private ComputerBlock() {
    }

    public static Map<String, Object> note(String text, String tone) {
        return note(text, tone, null);
    }

    /**
     * @param kind optional semantic key (e.g. assumptions); FE maps to locale prefix
     */
    public static Map<String, Object> note(String text, String tone, String kind) {
        Map<String, Object> block = new LinkedHashMap<String, Object>();
        block.put("type", "note");
        block.put("text", text);
        if (StringUtils.hasText(tone)) {
            block.put("tone", tone);
        }
        if (StringUtils.hasText(kind)) {
            block.put("kind", kind);
        }
        return block;
    }

    public static Map<String, Object> list(boolean ordered, List<Map<String, Object>> items) {
        Map<String, Object> block = new LinkedHashMap<String, Object>();
        block.put("type", "list");
        block.put("ordered", Boolean.valueOf(ordered));
        block.put("items", items == null ? new ArrayList<Map<String, Object>>() : items);
        return block;
    }

    public static Map<String, Object> markdown(String text) {
        Map<String, Object> block = new LinkedHashMap<String, Object>();
        block.put("type", "markdown");
        block.put("text", text == null ? "" : text);
        return block;
    }
}

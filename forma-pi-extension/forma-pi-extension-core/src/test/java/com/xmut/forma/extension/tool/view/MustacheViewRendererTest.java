package com.xmut.forma.extension.tool.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MustacheViewRendererTest {

    @Test
    void escapesHtmlInAttributes() {
        MustacheViewRenderer r = new MustacheViewRenderer();
        String html = r.render(
                "<button data-forma-prompt=\"{{handoffPrompt}}\">x</button>",
                Collections.singletonMap("handoffPrompt", "说\"你好\""));
        assertFalse(html.contains("data-forma-prompt=\"说\"你好\"\""));
        assertTrue(html.contains("&quot;") || html.contains("&#34;"));
    }

    @Test
    void loopsItems() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("displayTitle", "拓展坞");
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("items", Collections.singletonList(item));
        String html = new MustacheViewRenderer().render(
                "{{#items}}<li>{{displayTitle}}</li>{{/items}}", root);
        assertEquals("<li>拓展坞</li>", html);
    }
}

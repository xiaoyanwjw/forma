package com.xmut.forma.extension.tool.view;

import com.samskivert.mustache.Mustache;
import java.util.Map;

public final class MustacheViewRenderer {

    /**
     * @param template Mustache source
     * @param data root map (artifact-shaped)
     */
    public String render(String template, Map<String, Object> data) {
        return Mustache.compiler().escapeHTML(true).compile(template).execute(data);
    }
}

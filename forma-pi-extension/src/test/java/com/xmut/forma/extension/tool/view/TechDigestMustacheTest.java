package com.xmut.forma.extension.tool.view;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechDigestMustacheTest {

    @Test
    void techDigestTemplateRendersTitleAndQuote() throws Exception {
        String tpl = readClasspath("scenes/tech_digest/tech-digest/template/view.mustache");
        Map<String, Object> excerpt = new LinkedHashMap<String, Object>();
        excerpt.put("heading", "能力");
        excerpt.put("quotes", Collections.singletonList("支持本地部署。"));
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("title", "示例速读");
        root.put("oneLiner", "这是一款本地模型工具。");
        root.put("points", Collections.singletonList("可离线运行"));
        root.put("excerpts", Collections.singletonList(excerpt));
        root.put("forWhom", "小团队");
        String html = new MustacheViewRenderer().render(tpl, root);
        assertTrue(html.contains("示例速读"));
        assertTrue(html.contains("支持本地部署。"));
        assertTrue(html.contains("AI 摘要，请对照原文"));
    }

    private static String readClasspath(String path) throws Exception {
        InputStream in = TechDigestMustacheTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        try (InputStream stream = in; Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

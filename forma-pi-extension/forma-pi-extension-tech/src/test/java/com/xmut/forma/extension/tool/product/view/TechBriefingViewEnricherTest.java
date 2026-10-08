package com.xmut.forma.extension.tool.product.view;

import com.xmut.forma.extension.tool.view.MustacheViewRenderer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fixture artifact → enrich → Mustache 片段含 topic 与至少一条 title。
 */
class TechBriefingViewEnricherTest {

    @Test
    void enrich_thenRender_containsTopicAndItemTitle() throws Exception {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("title", "Cursor Rules Hub");
        item.put("oneLiner", "面向团队的 Cursor rules 分享与发现目录。");
        item.put("whyNow", "刚上 PH，讨论多，适合跟规则分发形态。");
        item.put("sourceUrl", "https://www.producthunt.com/posts/example");
        item.put("status", "found");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item);

        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("topic", "AI coding agents");
        artifact.put("windowLabel", "今天");
        artifact.put("source", "ph");
        artifact.put("deepFetch", Integer.valueOf(1));
        artifact.put("items", items);
        artifact.put("uncertainties", java.util.Collections.singletonList("列表源仅 Product Hunt"));

        TechBriefingViewEnricher enricher = new TechBriefingViewEnricher();
        assertEquals("tech-briefing", enricher.skillId());

        Map<String, Object> enriched = enricher.enrich(artifact);
        String tpl = readClasspath("scenes/tech_product/tech-briefing/template/view.mustache");
        String html = new MustacheViewRenderer().render(tpl, enriched);

        assertTrue(html.contains("AI coding agents"), html);
        assertTrue(html.contains("Cursor Rules Hub"), html);
        assertTrue(html.contains("今天"), html);
        assertTrue(html.contains("是什么") || html.contains("面向团队"), html);
        assertTrue(html.contains("https://www.producthunt.com/posts/example"), html);
        assertFalse(html.contains("data-forma-action=\"handoff\""), html);
    }

    private static String readClasspath(String path) throws Exception {
        InputStream in = TechBriefingViewEnricherTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        try (InputStream stream = in; Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

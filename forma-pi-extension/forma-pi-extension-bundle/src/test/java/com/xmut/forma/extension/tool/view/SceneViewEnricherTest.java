package com.xmut.forma.extension.tool.view;

import com.xmut.forma.extension.tool.digest.view.TechDigestViewEnricher;
import com.xmut.forma.extension.tool.product.view.TechBriefingViewEnricher;
import com.xmut.forma.extension.tool.product.view.TechCompetitorViewEnricher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景模块 enricher 契约（classpath 含 ecommerce / xhs / tech）。
 */
class SceneViewEnricherTest {

    private ViewEnricherComposite registry;

    @BeforeEach
    void setUp() {
        registry = ViewEnricherComposite.of(
                new XhsTopiclistViewEnricher(),
                new EcommercePicklistViewEnricher(),
                new XhsBreakViewEnricher(),
                new XhsNoteViewEnricher(),
                new EcommerceSkulistViewEnricher(),
                new TechDigestViewEnricher(),
                new TechCompetitorViewEnricher(),
                new TechBriefingViewEnricher());
    }

    @Test
    void enrich_topiclist_addsDisplayTitleAndHandoff_withoutMutatingSource() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", "tp-1");
        item.put("title", "【优先发】Mini 拓展坞");
        item.put("hook", "线太乱？");
        item.put("angle", "桌搭");
        item.put("sourceNoteUrl", "https://www.xiaohongshu.com/explore/n1");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        items.add(item);
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "选题");
        artifact.put("items", items);

        Map<String, Object> enriched = registry.enrich("xhs-topiclist", artifact);

        assertNotSame(artifact, enriched);
        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) enriched.get("items")).get(0);
        assertEquals("Mini 拓展坞", outItem.get("displayTitle"));
        String prompt = (String) outItem.get("handoffPrompt");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Mini 拓展坞"));
        assertFalse(prompt.contains("【优先发】"));
        assertTrue(prompt.contains("条目 tp-1"));
        assertTrue(prompt.contains("角度：桌搭"));
        assertTrue(prompt.contains("钩子：线太乱？"));
        assertTrue(prompt.contains("原笔记：https://www.xiaohongshu.com/explore/n1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceItem = (Map<String, Object>) ((List<?>) artifact.get("items")).get(0);
        assertFalse(sourceItem.containsKey("displayTitle"));
        assertFalse(sourceItem.containsKey("handoffPrompt"));
    }

    @Test
    void enrich_topiclist_omitsHandoffWhenIdMissing() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("title", "仅标题");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("items", java.util.Collections.singletonList(item));

        Map<String, Object> enriched = registry.enrich("xhs-topiclist", artifact);
        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) enriched.get("items")).get(0);
        assertEquals("仅标题", outItem.get("displayTitle"));
        assertFalse(outItem.containsKey("handoffPrompt"));
    }

    @Test
    void enrich_picklist_requiresHttpsSourceUrl() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", "pl-1");
        item.put("title", "垫");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("items", java.util.Collections.singletonList(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) registry
                .enrich("ecommerce-picklist", artifact).get("items")).get(0);
        assertFalse(outItem.containsKey("handoffPrompt"));
    }

    @Test
    void enrich_picklist_buildsListingHandoff() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", "pl-2");
        item.put("title", "【优先试】硅胶沥水垫");
        item.put("sourceUrl", "https://item.example/1");
        item.put("niche", "租房厨房");
        item.put("painPoint", "水渍");
        item.put("angle", "小户型");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("items", java.util.Collections.singletonList(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) registry
                .enrich("ecommerce-picklist", artifact).get("items")).get(0);
        assertEquals("硅胶沥水垫", outItem.get("displayTitle"));
        assertEquals(Boolean.TRUE, outItem.get("priorityTry"));
        assertEquals("01", outItem.get("indexLabel"));
        String prompt = (String) outItem.get("handoffPrompt");
        assertTrue(prompt.contains("请为商品「硅胶沥水垫」生成上架素材。"));
        assertTrue(prompt.contains("原链：https://item.example/1"));
        assertTrue(prompt.contains("来源选品条目：pl-2"));
        assertTrue(prompt.contains("参考：租房厨房"));
        assertTrue(prompt.contains("痛点：水渍"));
        assertTrue(prompt.contains("角度：小户型"));
    }

    @Test
    void enrich_picklist_mapsScoreTonesAndItemCount() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", "pl-1");
        item.put("title", "拓展坞");
        item.put("demand", "高｜接口焦虑");
        item.put("competition", "偏高｜同质");
        item.put("margin", "中｜一般");
        item.put("risk", "低｜可控");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("items", java.util.Collections.singletonList(item));

        Map<String, Object> enriched = registry.enrich("ecommerce-picklist", artifact);
        assertEquals(Integer.valueOf(1), enriched.get("itemCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) enriched.get("items")).get(0);
        assertEquals("高", outItem.get("demandLabel"));
        assertEquals("ok", outItem.get("demandClass"));
        assertEquals("偏高", outItem.get("competitionLabel"));
        assertEquals("warn", outItem.get("competitionClass"));
        assertEquals("中", outItem.get("marginLabel"));
        assertEquals("mid", outItem.get("marginClass"));
        assertEquals("低", outItem.get("riskLabel"));
        assertEquals("gray", outItem.get("riskClass"));
    }

    @Test
    void enrich_topiclist_marksPriorityPublish() {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", "tp-1");
        item.put("title", "【优先发】线材消失术");
        item.put("risk", "硬广感");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("items", java.util.Collections.singletonList(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) registry
                .enrich("xhs-topiclist", artifact).get("items")).get(0);
        assertEquals(Boolean.TRUE, outItem.get("priorityPublish"));
        assertEquals("优先发", outItem.get("priorityLabel"));
        assertEquals("pink", outItem.get("priorityClass"));
        assertEquals("硬广感", outItem.get("riskLabel"));
        assertEquals("mid", outItem.get("riskClass"));
    }

    @Test
    void enrich_skulist_buildsFrameEntriesWithoutPrompts() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("frames", java.util.Arrays.asList("白底", "对比"));
        artifact.put("modules", java.util.Collections.singletonList("卖点"));

        Map<String, Object> enriched = registry.enrich("ecommerce-skulist", artifact);
        assertEquals(Boolean.TRUE, enriched.get("hasFrames"));
        assertEquals(Boolean.TRUE, enriched.get("hasModules"));
        assertEquals(Integer.valueOf(2), enriched.get("frameCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) enriched.get("frameEntries");
        assertEquals("01", entries.get(0).get("indexLabel"));
        assertEquals(Boolean.TRUE, entries.get(0).get("isFirst"));
        assertEquals("白底", entries.get(0).get("frame"));
        assertFalse(entries.get(0).containsKey("prompt"));
    }

    @Test
    void enrich_break_buildsHandoffWithTruncation() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("targetProduct", "Mac Mini 拓展坞");
        artifact.put("structure", repeat('s', 300));
        artifact.put("skeleton", "骨架一句");
        artifact.put("rewrite", "改写一句");

        Map<String, Object> enriched = registry.enrich("xhs-break", artifact);

        String prompt = (String) enriched.get("handoffPrompt");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Mac Mini 拓展坞"));
        assertTrue(prompt.contains("结构要点："));
        assertTrue(prompt.contains(repeat('s', 240) + "…"));
        assertTrue(prompt.contains("骨架：骨架一句"));
    }

    @Test
    void enrich_note_addsNumberedListsAndTagsDisplay() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("titleOptions", java.util.Arrays.asList("标题 A", "标题 B"));
        artifact.put("imageHints", java.util.Collections.singletonList("首图提示"));
        artifact.put("tags", java.util.Arrays.asList("Mac Mini", "桌搭"));

        Map<String, Object> enriched = registry.enrich("xhs-note", artifact);

        @SuppressWarnings("unchecked")
        List<String> titles = (List<String>) enriched.get("titleOptionsNumbered");
        assertEquals("1. 标题 A", titles.get(0));
        assertEquals("Mac Mini · 桌搭", enriched.get("tagsDisplay"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.get("tagItems");
        assertEquals(2, tags.size());
        assertEquals("Mac Mini", tags.get(0));
    }

    @Test
    void enrich_techDigest_defaultsEmptyListsAndHasUncertaintiesFlag() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "速读标题");

        Map<String, Object> enriched = registry.enrich("tech-digest", artifact);

        assertNotSame(artifact, enriched);
        assertTrue(enriched.get("points") instanceof List);
        assertTrue(((List<?>) enriched.get("points")).isEmpty());
        assertTrue(enriched.get("excerpts") instanceof List);
        assertTrue(((List<?>) enriched.get("excerpts")).isEmpty());
        assertTrue(enriched.get("uncertainties") instanceof List);
        assertTrue(((List<?>) enriched.get("uncertainties")).isEmpty());
        assertNull(enriched.get("hasUncertainties"));
        assertEquals("0", enriched.get("pointCount"));
        assertEquals("原文", enriched.get("sourceLabel"));

        Map<String, Object> withUncertainties = new LinkedHashMap<String, Object>();
        withUncertainties.put("title", "t");
        withUncertainties.put("source", "fetch");
        withUncertainties.put("sourceUrl", "https://example.com/x");
        withUncertainties.put("points", java.util.Collections.singletonList("可离线"));
        withUncertainties.put("uncertainties", java.util.Collections.singletonList("待核实"));
        Map<String, Object> flagged = registry.enrich("tech-digest", withUncertainties);
        assertEquals(Boolean.TRUE, flagged.get("hasUncertainties"));
        assertEquals("公开链接", flagged.get("sourceLabel"));
        assertEquals("info", flagged.get("sourceClass"));
        assertEquals("1", flagged.get("pointCount"));
        assertEquals(Boolean.TRUE, flagged.get("hasSourceUrl"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pointItems = (List<Map<String, Object>>) flagged.get("pointItems");
        assertEquals("01", pointItems.get(0).get("indexLabel"));
        assertEquals("可离线", pointItems.get(0).get("text"));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}

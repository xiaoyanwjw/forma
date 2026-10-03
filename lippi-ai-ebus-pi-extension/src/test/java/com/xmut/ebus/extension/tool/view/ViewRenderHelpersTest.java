package com.xmut.ebus.extension.tool.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRenderHelpersTest {

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

        Map<String, Object> enriched = ViewRenderHelpers.enrich("xhs-topiclist", artifact);

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

        Map<String, Object> enriched = ViewRenderHelpers.enrich("xhs-topiclist", artifact);
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
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) ViewRenderHelpers
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
        Map<String, Object> outItem = (Map<String, Object>) ((List<?>) ViewRenderHelpers
                .enrich("ecommerce-picklist", artifact).get("items")).get(0);
        assertEquals("硅胶沥水垫", outItem.get("displayTitle"));
        String prompt = (String) outItem.get("handoffPrompt");
        assertTrue(prompt.contains("请为商品「硅胶沥水垫」生成上架素材。"));
        assertTrue(prompt.contains("原链：https://item.example/1"));
        assertTrue(prompt.contains("来源选品条目：pl-2"));
        assertTrue(prompt.contains("参考：租房厨房"));
        assertTrue(prompt.contains("痛点：水渍"));
        assertTrue(prompt.contains("角度：小户型"));
    }

    @Test
    void enrich_unknownSkill_returnsIdentity() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "x");
        assertEquals(artifact, ViewRenderHelpers.enrich("other-skill", artifact));
    }
}

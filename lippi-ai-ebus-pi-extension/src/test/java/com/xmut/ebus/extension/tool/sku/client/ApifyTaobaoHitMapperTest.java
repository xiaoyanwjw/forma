package com.xmut.ebus.extension.tool.sku.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.ebus.extension.tool.sku.port.SkuSearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyTaobaoHitMapperTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void maps_https_row_and_skips_non_https() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode ok = arr.addObject();
        ok.put("titleOriginal", "拓展坞");
        ok.put("price", 29.9);
        ok.put("url", "https://item.taobao.com/item.htm?id=1");
        ok.put("itemId", "1");
        ObjectNode bad = arr.addObject();
        bad.put("title", "x");
        bad.put("url", "http://evil.example/1");
        List<SkuSearchHit> hits = ApifyTaobaoHitMapper.mapItems(arr);
        assertEquals(1, hits.size());
        assertEquals("taobao_apify", hits.get(0).getPlatform());
        assertEquals("拓展坞", hits.get(0).getTitle());
        assertTrue(hits.get(0).getDetailUrl().startsWith("https://"));
    }

    @Test
    void category_uses_crumbs_then_cat_id() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode withCrumbs = arr.addObject();
        withCrumbs.put("title", "a");
        withCrumbs.put("price", "1");
        withCrumbs.put("url", "https://item.taobao.com/item.htm?id=2");
        withCrumbs.put("itemId", "2");
        withCrumbs.putArray("categoryCrumbs").add("家居").add("收纳");
        ObjectNode withId = arr.addObject();
        withId.put("title", "b");
        withId.put("price", "2");
        withId.put("url", "https://item.taobao.com/item.htm?id=3");
        withId.put("itemId", "3");
        withId.put("categoryId", "1512");
        List<SkuSearchHit> hits = ApifyTaobaoHitMapper.mapItems(arr);
        assertEquals(2, hits.size());
        assertEquals("家居/收纳", hits.get(0).getCategory());
        assertEquals("cat:1512", hits.get(1).getCategory());
    }
}

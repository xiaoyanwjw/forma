package com.xmut.ebus.application.business.picklist.support;

import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PicklistViewProjectorTest {

    @Test
    void projectsStructuredLineKindsWithoutConcatenatedReason() {
        List<PicklistArtifactDTO.PicklistItemDTO> items = new ArrayList<PicklistArtifactDTO.PicklistItemDTO>();
        items.add(new PicklistArtifactDTO.PicklistItemDTO(
                "【优先试】硅胶垫", "19-39",
                "水槽边积水", "租房刚需", "多色套装", "厨房沥水",
                "高｜稳", "中｜可切", "中｜友好", "低｜合规",
                "https://item.example/pad"));
        items.add(new PicklistArtifactDTO.PicklistItemDTO(
                "置物架", "29-59", "台面乱", "免打孔", "伸缩", "收纳",
                "中｜x", "中｜y", "高｜z", "低｜w",
                "https://item.example/rack"));
        while (items.size() < 8) {
            int i = items.size();
            items.add(new PicklistArtifactDTO.PicklistItemDTO(
                    "品" + i, "19-39", "痛点" + i, "切入" + i, "差异" + i,
                    "细分" + (i % 3), "高｜d", "中｜c", "中｜m", "低｜r",
                    "https://item.example/" + i));
        }
        PicklistArtifactDTO dto = new PicklistArtifactDTO(
                "pl-1", "run-1", "domestic-generic-default",
                "基于通用知识推断，非实时平台数据", "默认假设", items);

        Map<String, Object> view = new PicklistViewProjector().project(dto);

        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("picklist", view.get("title"));
        assertEquals("settled", view.get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals("note", blocks.get(0).get("type"));
        assertTrue(String.valueOf(blocks.get(0).get("text")).contains("非实时"));
        assertEquals("note", blocks.get(1).get("type"));
        assertEquals("assumptions", blocks.get(1).get("kind"));
        assertEquals("默认假设", blocks.get(1).get("text"));
        assertEquals("list", blocks.get(2).get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listItems = (List<Map<String, Object>>) blocks.get(2).get("items");
        assertEquals("priority", listItems.get(0).get("badge"));
        assertEquals("硅胶垫", listItems.get(0).get("title"));
        assertEquals("https://item.example/pad", listItems.get(0).get("href"));
        assertEquals("https://item.example/rack", listItems.get(1).get("href"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) listItems.get(0).get("lines");
        assertEquals("priceBand", lines.get(0).get("kind"));
        assertEquals("19-39", lines.get(0).get("text"));
        assertEquals("price", lines.get(0).get("emphasis"));
        assertEquals("painPoint", lines.get(1).get("kind"));
        assertEquals("水槽边积水", lines.get(1).get("text"));
        assertEquals("angle", lines.get(2).get("kind"));
        assertEquals("租房刚需", lines.get(2).get("text"));
        assertEquals("diff", lines.get(3).get("kind"));
        assertEquals("多色套装", lines.get(3).get("text"));
        assertEquals("niche", lines.get(4).get("kind"));
        assertEquals("厨房沥水", lines.get(4).get("text"));
        assertFalse(lines.get(0).containsKey("label"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) listItems.get(0).get("tags");
        assertEquals(4, tags.size());
        assertEquals("demand", tags.get(0).get("kind"));
        assertEquals("高｜稳", tags.get(0).get("text"));
        assertEquals("positive", tags.get(0).get("tone"));
        assertEquals("competition", tags.get(1).get("kind"));
        assertEquals("caution", tags.get(1).get("tone"));
        assertEquals("margin", tags.get(2).get("kind"));
        assertEquals("info", tags.get(2).get("tone"));
        assertEquals("risk", tags.get(3).get("kind"));
        assertEquals("safe", tags.get(3).get("tone"));
    }
}

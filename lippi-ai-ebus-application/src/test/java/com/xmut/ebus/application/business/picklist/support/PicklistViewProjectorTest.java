package com.xmut.ebus.application.business.picklist.support;

import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PicklistViewProjectorTest {

    @Test
    void projectsDisclaimerAssumptionsPriorityAndDims() {
        List<PicklistArtifactDTO.PicklistItemDTO> items = new ArrayList<PicklistArtifactDTO.PicklistItemDTO>();
        items.add(new PicklistArtifactDTO.PicklistItemDTO(
                "【优先试】硅胶垫", "19-39",
                "痛点：积水；切入：刚需；差异：多色",
                "细分：厨房；多色",
                "高｜稳", "中｜可切", "中｜友好", "低｜合规"));
        items.add(new PicklistArtifactDTO.PicklistItemDTO(
                "置物架", "29-59", "痛点：a；切入：b；差异：c",
                "细分：收纳；免打孔", "中｜x", "中｜y", "高｜z", "低｜w"));
        while (items.size() < 8) {
            int i = items.size();
            items.add(new PicklistArtifactDTO.PicklistItemDTO(
                    "品" + i, "19-39", "痛点：p；切入：c；差异：d",
                    "细分：细分" + (i % 3) + "；x", "高｜d", "中｜c", "中｜m", "低｜r"));
        }
        PicklistArtifactDTO dto = new PicklistArtifactDTO(
                "pl-1", "run-1", "domestic-generic-default",
                "基于通用知识推断，非实时平台数据", "默认假设", items);

        Map<String, Object> view = new PicklistViewProjector().project(dto);

        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("选品清单", view.get("title"));
        assertEquals("已结算", view.get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals("note", blocks.get(0).get("type"));
        assertTrue(String.valueOf(blocks.get(0).get("text")).contains("非实时"));
        assertEquals("note", blocks.get(1).get("type"));
        assertTrue(String.valueOf(blocks.get(1).get("text")).startsWith("假设："));
        assertEquals("list", blocks.get(2).get("type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listItems = (List<Map<String, Object>>) blocks.get(2).get("items");
        assertEquals("优先试", listItems.get(0).get("badge"));
        assertEquals("硅胶垫", listItems.get(0).get("title"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) listItems.get(0).get("tags");
        assertTrue(tags.get(0).startsWith("需求 "));
    }
}

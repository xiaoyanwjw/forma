package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.support.PicklistViewProjector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewProjectorChainTest {

    @Test
    void prefersNormalizeWhenRawViewPresent() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "report");
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> md = new LinkedHashMap<String, Object>();
        md.put("type", "markdown");
        md.put("text", "x");
        blocks.add(md);
        raw.put("blocks", blocks);

        ViewProjectorChain chain = new ViewProjectorChain(Arrays.asList(
                new NormalizeViewProjector(),
                new LegacyPicklistFallbackProjector(new PicklistViewProjector()),
                new NoSkillMarkdownProjector()));

        Optional<Map<String, Object>> view = chain.project(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("ignored")
                .rawView(raw)
                .build());
        assertTrue(view.isPresent());
        assertEquals("report", view.get().get("title"));
    }

    @Test
    void usesNoSkillMarkdownWhenNoViewAndNoSkill() {
        ViewProjectorChain chain = new ViewProjectorChain(Arrays.asList(
                new NormalizeViewProjector(),
                new LegacyPicklistFallbackProjector(new PicklistViewProjector()),
                new NoSkillMarkdownProjector()));

        Optional<Map<String, Object>> view = chain.project(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("plain")
                .build());
        assertTrue(view.isPresent());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get().get("blocks");
        assertEquals("markdown", blocks.get(0).get("type"));
        assertEquals("plain", blocks.get(0).get("text"));
    }

    @Test
    void usesLegacyPicklistWhenSkillBoundArtifactWithoutView() {
        List<PicklistArtifactDTO.PicklistItemDTO> items = new ArrayList<PicklistArtifactDTO.PicklistItemDTO>();
        for (int i = 0; i < 8; i++) {
            items.add(new PicklistArtifactDTO.PicklistItemDTO(
                    (i == 0 ? "【优先试】" : "") + "品" + i,
                    "19-39",
                    "痛点", "切入", "差异", "细分" + (i % 3),
                    "高｜d", "中｜c", "中｜m", "低｜r"));
        }
        PicklistArtifactDTO dto = new PicklistArtifactDTO(
                "pl-1", "run-1", "domestic-generic-default",
                "基于通用知识推断，非实时平台数据", null, items);

        ViewProjectorChain chain = new ViewProjectorChain(Arrays.asList(
                new NormalizeViewProjector(),
                new LegacyPicklistFallbackProjector(new PicklistViewProjector()),
                new NoSkillMarkdownProjector()));

        Optional<Map<String, Object>> view = chain.project(ViewProjectContext.builder()
                .skillBound(true)
                .artifact(dto)
                .build());
        assertTrue(view.isPresent());
        assertEquals("picklist", view.get().get("title"));
    }
}

package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.application.business.picklist.support.PicklistViewProjector;
import com.xmut.ebus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerViewResolverTest {

    private static ComputerViewResolver defaultResolver() {
        return new ComputerViewResolver(Arrays.asList(
                new NormalizeViewProjector(),
                new LegacyPicklistFallbackProjector(new PicklistViewProjector()),
                new NoSkillMarkdownProjector()));
    }

    @Test
    void resolveFailsWhenChainMisses() {
        ComputerViewResolver resolver = new ComputerViewResolver(
                Collections.<ComputerViewProjector>emptyList());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.resolve(ViewProjectContext.builder().skillBound(true).build()));
        assertEquals(ComputerViewResolver.MSG_VIEW_UNAVAILABLE, ex.getMessage());
    }

    @Test
    void resolveReturnsNormalizedSkillView() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", 1);
        raw.put("title", "report");
        Map<String, Object> note = new LinkedHashMap<String, Object>();
        note.put("type", "note");
        note.put("text", "hello");
        raw.put("blocks", Collections.singletonList(note));
        ComputerViewResolver resolver = new ComputerViewResolver(
                Collections.<ComputerViewProjector>singletonList(new NormalizeViewProjector()));
        Map<String, Object> view = resolver.resolve(ViewProjectContext.builder().rawView(raw).build());
        assertEquals("report", view.get("title"));
        assertTrue(view.containsKey("blocks"));
    }

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

        Map<String, Object> view = defaultResolver().resolve(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("ignored")
                .rawView(raw)
                .build());
        assertEquals("report", view.get("title"));
    }

    @Test
    void usesNoSkillMarkdownWhenNoViewAndNoSkill() {
        Map<String, Object> view = defaultResolver().resolve(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("plain")
                .build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
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

        Map<String, Object> view = defaultResolver().resolve(ViewProjectContext.builder()
                .skillBound(true)
                .artifact(dto)
                .build());
        assertEquals("picklist", view.get("title"));
    }
}

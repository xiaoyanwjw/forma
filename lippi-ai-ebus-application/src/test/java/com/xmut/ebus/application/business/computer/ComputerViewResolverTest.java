package com.xmut.ebus.application.business.computer;

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
    void failsWhenSkillBoundWithoutRawView() {
        BusinessException ex = assertThrows(BusinessException.class, () -> defaultResolver().resolve(
                ViewProjectContext.builder()
                        .skillBound(true)
                        .finalResponse("plain picklist json without view")
                        .build()));
        assertEquals(ComputerViewResolver.MSG_VIEW_UNAVAILABLE, ex.getMessage());
    }
}

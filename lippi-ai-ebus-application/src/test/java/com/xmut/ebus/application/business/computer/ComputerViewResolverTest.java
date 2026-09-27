package com.xmut.ebus.application.business.computer;

import com.xmut.ebus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerViewResolverTest {

    @Test
    void resolveFailsWhenChainMisses() {
        ComputerViewResolver resolver = new ComputerViewResolver(
                new ViewProjectorChain(Collections.<ComputerViewProjector>emptyList()));
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
        ComputerViewResolver resolver = new ComputerViewResolver(new ViewProjectorChain(
                Collections.<ComputerViewProjector>singletonList(new NormalizeViewProjector())));
        Map<String, Object> view = resolver.resolve(ViewProjectContext.builder().rawView(raw).build());
        assertEquals("report", view.get("title"));
        assertTrue(view.containsKey("blocks"));
    }
}

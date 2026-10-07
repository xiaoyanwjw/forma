package com.xmut.forma.extension.tool.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewEnricherCompositeTest {

    @Test
    void enrich_unknownSkill_returnsIdentity() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "x");
        ViewEnricherComposite composite = ViewEnricherComposite.empty();
        assertSame(artifact, composite.enrich("other-skill", artifact));
        assertEquals(0, composite.size());
    }

    @Test
    void enrich_dispatchesRegisteredEnricher() {
        ViewEnricherComposite composite = ViewEnricherComposite.of(new ViewEnricher() {
            @Override
            public String skillId() {
                return "demo";
            }

            @Override
            public Map<String, Object> enrich(Map<String, Object> artifact) {
                artifact.put("flag", Boolean.TRUE);
                return artifact;
            }
        });
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "t");
        Map<String, Object> out = composite.enrich("demo", artifact);
        assertEquals(Boolean.TRUE, out.get("flag"));
        assertEquals(null, artifact.get("flag"));
    }

    @Test
    void duplicateSkillId_failsFast() {
        ViewEnricher a = stub("dup");
        ViewEnricher b = stub("dup");
        assertThrows(IllegalStateException.class, () -> new ViewEnricherComposite(
                java.util.Arrays.asList(a, b)));
    }

    private static ViewEnricher stub(final String id) {
        return new ViewEnricher() {
            @Override
            public String skillId() {
                return id;
            }

            @Override
            public Map<String, Object> enrich(Map<String, Object> artifact) {
                return artifact == null ? Collections.<String, Object>emptyMap() : artifact;
            }
        };
    }
}

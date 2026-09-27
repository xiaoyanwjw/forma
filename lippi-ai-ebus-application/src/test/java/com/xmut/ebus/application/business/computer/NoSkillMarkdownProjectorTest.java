package com.xmut.ebus.application.business.computer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoSkillMarkdownProjectorTest {

    private final NoSkillMarkdownProjector projector = new NoSkillMarkdownProjector();

    @Test
    void supportsOnlyWhenNoSkillAndHasText() {
        assertTrue(projector.supports(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("hello")
                .build()));
        assertFalse(projector.supports(ViewProjectContext.builder()
                .skillBound(true)
                .finalResponse("hello")
                .build()));
        assertFalse(projector.supports(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("  ")
                .build()));
    }

    @Test
    void wrapsFinalResponseAsMarkdownBlock() {
        Map<String, Object> view = projector.project(ViewProjectContext.builder()
                .skillBound(false)
                .finalResponse("  hello world  ")
                .build());
        assertEquals(Integer.valueOf(1), view.get("version"));
        assertEquals("draft", view.get("title"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals(1, blocks.size());
        assertEquals("markdown", blocks.get(0).get("type"));
        assertEquals("hello world", blocks.get(0).get("text"));
    }
}

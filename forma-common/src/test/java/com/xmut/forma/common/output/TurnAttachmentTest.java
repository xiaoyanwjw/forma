package com.xmut.forma.common.output;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnAttachmentTest {

    @Test
    void ofNullEqualsEmptyAndIsEmpty() {
        TurnAttachment empty = TurnAttachment.empty();
        TurnAttachment fromNull = TurnAttachment.of(null);

        assertTrue(empty.isEmpty());
        assertTrue(fromNull.isEmpty());
        assertEquals(empty, fromNull);
        assertEquals(empty.hashCode(), fromNull.hashCode());
    }

    @Test
    void ofEmptyMapEqualsEmpty() {
        TurnAttachment fromEmpty = TurnAttachment.of(new HashMap<String, Object>());

        assertTrue(fromEmpty.isEmpty());
        assertEquals(TurnAttachment.empty(), fromEmpty);
    }

    @Test
    void mutatingSourceMapDoesNotChangeAttachment() {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("viewPath", "exec/view.json");
        TurnAttachment attachment = TurnAttachment.of(raw);

        raw.put("viewPath", "changed");
        raw.put("extra", "x");

        assertEquals("exec/view.json", attachment.get("viewPath"));
        assertNull(attachment.get("extra"));
    }

    @Test
    void ofDropsNullKeysAndKeepsNullValues() {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("viewPath", "exec/view.json");
        raw.put(null, "skip-me");
        raw.put("missing", null);

        TurnAttachment attachment = TurnAttachment.of(raw);

        assertEquals("exec/view.json", attachment.get("viewPath"));
        assertNull(attachment.get("missing"));
        assertEquals(2, attachment.asMap().size());
    }

    @Test
    void getReadsViewPathString() {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("viewPath", "exec/view.json");

        TurnAttachment attachment = TurnAttachment.of(raw);

        assertEquals("exec/view.json", attachment.get("viewPath"));
    }
}

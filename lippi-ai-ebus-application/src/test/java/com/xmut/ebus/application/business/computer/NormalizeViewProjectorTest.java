package com.xmut.ebus.application.business.computer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizeViewProjectorTest {

    private final NormalizeViewProjector projector = new NormalizeViewProjector();

    @Test
    void supportsWhenRawViewPresent() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "report");
        raw.put("blocks", new ArrayList<Object>());
        assertTrue(projector.supports(ViewProjectContext.builder().rawView(raw).build()));
        assertFalse(projector.supports(ViewProjectContext.builder().build()));
    }

    @Test
    void dropsUnknownBlockTypesAndKeepsWhitelist() {
        List<Object> blocks = new ArrayList<Object>();
        Map<String, Object> note = new LinkedHashMap<String, Object>();
        note.put("type", "note");
        note.put("text", "hello");
        note.put("tone", "mute");
        blocks.add(note);
        Map<String, Object> nope = new LinkedHashMap<String, Object>();
        nope.put("type", "nope");
        nope.put("text", "x");
        blocks.add(nope);
        Map<String, Object> md = new LinkedHashMap<String, Object>();
        md.put("type", "markdown");
        md.put("text", "# hi");
        blocks.add(md);

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "report");
        raw.put("blocks", blocks);

        Map<String, Object> view = projector.project(ViewProjectContext.builder().rawView(raw).build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outBlocks = (List<Map<String, Object>>) view.get("blocks");
        assertEquals(2, outBlocks.size());
        assertEquals("note", outBlocks.get(0).get("type"));
        assertEquals("markdown", outBlocks.get(1).get("type"));
    }

    @Test
    void passesThroughHttpsListItemHrefAndDropsNonHttps() {
        List<Object> items = new ArrayList<Object>();
        Map<String, Object> withHttps = new LinkedHashMap<String, Object>();
        withHttps.put("title", "A");
        withHttps.put("href", "https://item.example/1");
        items.add(withHttps);
        Map<String, Object> withHttp = new LinkedHashMap<String, Object>();
        withHttp.put("title", "B");
        withHttp.put("href", "http://item.example/2");
        items.add(withHttp);
        Map<String, Object> noHref = new LinkedHashMap<String, Object>();
        noHref.put("title", "C");
        items.add(noHref);

        Map<String, Object> listBlock = new LinkedHashMap<String, Object>();
        listBlock.put("type", "list");
        listBlock.put("items", items);

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "picklist");
        raw.put("blocks", Arrays.asList(listBlock));

        Map<String, Object> view = projector.project(ViewProjectContext.builder().rawView(raw).build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outBlocks = (List<Map<String, Object>>) view.get("blocks");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) outBlocks.get(0).get("items");
        assertEquals(3, outItems.size());
        assertEquals("https://item.example/1", outItems.get(0).get("href"));
        assertFalse(outItems.get(1).containsKey("href"));
        assertFalse(outItems.get(2).containsKey("href"));
    }

    @Test
    void preservesTrimmedListItemIdAndDropsBlank() {
        List<Object> items = new ArrayList<Object>();
        Map<String, Object> withId = new LinkedHashMap<String, Object>();
        withId.put("title", "A");
        withId.put("id", "  pl-1  ");
        withId.put("href", "https://item.example/1");
        items.add(withId);
        Map<String, Object> blankId = new LinkedHashMap<String, Object>();
        blankId.put("title", "B");
        blankId.put("id", "   ");
        blankId.put("href", "https://item.example/2");
        items.add(blankId);

        Map<String, Object> listBlock = new LinkedHashMap<String, Object>();
        listBlock.put("type", "list");
        listBlock.put("items", items);

        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "picklist");
        raw.put("blocks", Arrays.asList(listBlock));

        Map<String, Object> view = projector.project(ViewProjectContext.builder().rawView(raw).build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outBlocks = (List<Map<String, Object>>) view.get("blocks");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) outBlocks.get(0).get("items");
        assertEquals("pl-1", outItems.get(0).get("id"));
        assertFalse(outItems.get(1).containsKey("id"));
    }
}

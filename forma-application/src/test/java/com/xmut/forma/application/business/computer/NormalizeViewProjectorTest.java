package com.xmut.forma.application.business.computer;

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

    @Test
    void passesThroughLineAndTagDisplayLabels() {
        Map<String, Object> line = new LinkedHashMap<String, Object>();
        line.put("kind", "hook");
        line.put("label", "视角");
        line.put("text", "背后一串转接头");
        Map<String, Object> tag = new LinkedHashMap<String, Object>();
        tag.put("kind", "demand");
        tag.put("label", "需求");
        tag.put("text", "高");
        tag.put("tone", "positive");
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("title", "选题");
        item.put("lines", Arrays.asList(line));
        item.put("tags", Arrays.asList(tag));
        Map<String, Object> listBlock = new LinkedHashMap<String, Object>();
        listBlock.put("type", "list");
        listBlock.put("items", Arrays.asList(item));
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(1));
        raw.put("title", "选题清单");
        raw.put("blocks", Arrays.asList(listBlock));

        Map<String, Object> view = projector.project(ViewProjectContext.builder().rawView(raw).build());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outBlocks = (List<Map<String, Object>>) view.get("blocks");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) outBlocks.get(0).get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) outItems.get(0).get("lines");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) outItems.get(0).get("tags");
        assertEquals("视角", lines.get(0).get("label"));
        assertEquals("需求", tags.get(0).get("label"));
    }

    @Test
    void project_keepsV2HtmlDocument() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(2));
        raw.put("title", "选题清单");
        raw.put("format", "html");
        raw.put("content", "<h1>Hi</h1><button data-forma-action=\"handoff\" data-forma-skill-id=\"xhs-note\" data-forma-prompt=\"写笔记\">写成笔记</button>");
        Map<String, Object> out = projector.project(ViewProjectContext.builder().rawView(raw).build());
        assertEquals(Integer.valueOf(2), out.get("version"));
        assertEquals("html", out.get("format"));
        assertEquals("选题清单", out.get("title"));
        assertTrue(String.valueOf(out.get("content")).contains("data-forma-action"));
        assertFalse(out.containsKey("blocks"));
    }

    @Test
    void project_rejectsBlankV2ContentAsEmptyMap() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("version", Integer.valueOf(2));
        raw.put("title", "x");
        raw.put("format", "markdown");
        raw.put("content", "  ");
        Map<String, Object> out = projector.project(ViewProjectContext.builder().rawView(raw).build());
        assertTrue(out.isEmpty());
    }
}

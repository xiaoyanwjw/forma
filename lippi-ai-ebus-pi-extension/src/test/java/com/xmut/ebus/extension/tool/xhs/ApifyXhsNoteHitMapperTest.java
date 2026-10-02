package com.xmut.ebus.extension.tool.xhs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyXhsNoteHitMapperTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void maps_https_row_and_skips_non_https() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode ok = arr.addObject();
        ok.put("noteId", "abc");
        ok.put("title", "厨房收纳");
        ok.put("desc", "租房党");
        ok.put("noteUrl", "https://www.xiaohongshu.com/explore/abc");
        ok.put("likedCount", 12);
        ok.put("author", "小A");
        ObjectNode bad = arr.addObject();
        bad.put("title", "x");
        bad.put("url", "http://evil.example/1");
        List<XhsNoteSearchHit> hits = ApifyXhsNoteHitMapper.mapItems(arr);
        assertEquals(1, hits.size());
        assertEquals("abc", hits.get(0).getNoteId());
        assertEquals("厨房收纳", hits.get(0).getTitle());
        assertEquals("租房党", hits.get(0).getDesc());
        assertTrue(hits.get(0).getNoteUrl().startsWith("https://"));
        assertEquals("12", hits.get(0).getLikedCount());
        assertEquals("小A", hits.get(0).getAuthor());
    }

    @Test
    void keeps_row_when_https_url_present_and_title_empty() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode row = arr.addObject();
        row.put("title", "");
        row.put("noteUrl", "https://www.xiaohongshu.com/explore/no-title");
        List<XhsNoteSearchHit> hits = ApifyXhsNoteHitMapper.mapItems(arr);
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getTitle());
        assertEquals("https://www.xiaohongshu.com/explore/no-title", hits.get(0).getNoteUrl());
    }

    @Test
    void maps_url_alias_and_empty_optional_fields() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode row = arr.addObject();
        row.put("title", "杯垫");
        row.put("url", "https://www.xiaohongshu.com/explore/xyz");
        List<XhsNoteSearchHit> hits = ApifyXhsNoteHitMapper.mapItems(arr);
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getNoteId());
        assertEquals("杯垫", hits.get(0).getTitle());
        assertEquals("", hits.get(0).getDesc());
        assertEquals("https://www.xiaohongshu.com/explore/xyz", hits.get(0).getNoteUrl());
        assertEquals("", hits.get(0).getLikedCount());
        assertEquals("", hits.get(0).getAuthor());
    }
}

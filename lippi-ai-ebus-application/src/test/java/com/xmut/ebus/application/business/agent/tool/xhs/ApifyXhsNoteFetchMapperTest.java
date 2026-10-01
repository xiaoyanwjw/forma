package com.xmut.ebus.application.business.agent.tool.xhs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApifyXhsNoteFetchMapperTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void maps_first_row_with_body_aliases() {
        ArrayNode arr = om.createArrayNode();
        ObjectNode row = arr.addObject();
        row.put("title", "厨房收纳");
        row.put("desc", "抽屉整理全文");
        row.put("noteUrl", "https://www.xiaohongshu.com/explore/abc");
        row.put("author", "小A");
        row.putArray("tags").add("收纳").add("租房");
        Optional<XhsNoteFetchHit> hit = ApifyXhsNoteFetchMapper.mapFirst(arr);
        assertTrue(hit.isPresent());
        assertEquals("厨房收纳", hit.get().getTitle());
        assertEquals("抽屉整理全文", hit.get().getBody());
        assertEquals("https://www.xiaohongshu.com/explore/abc", hit.get().getNoteUrl());
        assertEquals("小A", hit.get().getAuthor());
        assertEquals(2, hit.get().getTags().size());
        assertEquals("收纳", hit.get().getTags().get(0));
    }

    @Test
    void empty_array_is_absent() {
        assertFalse(ApifyXhsNoteFetchMapper.mapFirst(om.createArrayNode()).isPresent());
    }
}

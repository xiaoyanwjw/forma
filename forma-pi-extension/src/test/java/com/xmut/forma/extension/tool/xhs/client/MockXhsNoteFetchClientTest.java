package com.xmut.forma.extension.tool.xhs.client;

import com.xmut.forma.extension.tool.xhs.port.XhsNoteFetchHit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockXhsNoteFetchClientTest {

    @Test
    void fetch_returns_fixed_body() {
        MockXhsNoteFetchClient client = new MockXhsNoteFetchClient();
        XhsNoteFetchHit hit = client.fetch("https://www.xiaohongshu.com/explore/abc");
        assertEquals("Mock 小红书笔记标题", hit.getTitle());
        assertTrue(hit.getBody() != null && hit.getBody().contains("Mock 小红书笔记正文"));
        assertEquals("https://www.xiaohongshu.com/explore/abc", hit.getNoteUrl());
    }

    @Test
    void fetch_blank_ref_throws() {
        MockXhsNoteFetchClient client = new MockXhsNoteFetchClient();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.fetch(""));
        assertTrue(ex.getMessage().contains("note_ref_required"));
    }
}

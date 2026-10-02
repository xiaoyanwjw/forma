package com.xmut.ebus.extension.tool.xhs.client;

import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchHit;
import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Fixed mock note catalog. Every hit has an https {@code noteUrl} on xiaohongshu.com/explore/.
 */
public final class MockXhsNoteSearchClient implements XhsNoteSearchPort {

    static final int SAMPLE_COUNT = 10;
    static final String NOTE_URL_PREFIX = "https://www.xiaohongshu.com/explore/mock-note-";

    @Override
    public List<XhsNoteSearchHit> search(String query, int pageSize) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String q = query.trim();
        int limit = pageSize < 1 ? 1 : Math.min(pageSize, SAMPLE_COUNT);
        List<XhsNoteSearchHit> hits = new ArrayList<XhsNoteSearchHit>(limit);
        for (int i = 1; i <= limit; i++) {
            hits.add(new XhsNoteSearchHit(
                    "mock-note-" + i,
                    q + " 种草笔记 " + i,
                    "Mock 笔记正文摘要 " + i + "，关键词：" + q,
                    NOTE_URL_PREFIX + i,
                    String.valueOf(100 + i),
                    "mock_author_" + i));
        }
        return Collections.unmodifiableList(hits);
    }
}

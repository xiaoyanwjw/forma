package com.xmut.ebus.extension.tool.xhs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsNoteSearcherTest {

    @Mock
    private XhsNoteSearchPort port;

    private XhsNoteSearchProperties props;
    private XhsNoteSearcher searcher;

    @BeforeEach
    void setUp() {
        props = new XhsNoteSearchProperties();
        searcher = newSearcher(port, XhsNoteReranker.identity());
    }

    @Test
    void expandQuery_trimsToSingleton() {
        XhsNoteSearcher s = newSearcher(port, XhsNoteReranker.identity());
        assertEquals(Collections.singletonList("杯垫"), s.expandQuery(" 杯垫 "));
    }

    @Test
    void doCheck_drops_non_https_and_empty_title() {
        List<XhsNoteCandidate> out = searcher.doCheck(Arrays.asList(
                candidate("h1", "ok", "https://www.xiaohongshu.com/explore/1"),
                candidate("h2", "", "https://www.xiaohongshu.com/explore/2"),
                candidate("h3", "x", "http://insecure")));
        assertEquals(1, out.size());
        assertEquals("h1", out.get(0).getId());
    }

    @Test
    void search_enabled_pipeline_returns_topHits() {
        List<XhsNoteSearchHit> fifteen = new ArrayList<XhsNoteSearchHit>();
        for (int i = 1; i <= 15; i++) {
            fifteen.add(hitHttps("title-" + i, i));
        }
        when(port.search(eq("q"), anyInt())).thenReturn(fifteen);
        assertEquals(10, searcher.search("q", 10).size());
    }

    @Test
    void search_disabled_short_circuits_to_port() {
        props.getSearcher().setEnabled(false);
        XhsNoteSearchHit insecure = new XhsNoteSearchHit(
                "n1", "", "desc", "http://insecure", "0", "a");
        when(port.search(eq("q"), eq(5))).thenReturn(Collections.singletonList(insecure));
        List<XhsNoteSearchHit> out = searcher.search("q", 5);
        assertEquals(1, out.size());
        assertEquals("http://insecure", out.get(0).getNoteUrl());
        verify(port).search("q", 5);
    }

    private XhsNoteSearcher newSearcher(XhsNoteSearchPort searchPort, XhsNoteReranker reranker) {
        return new XhsNoteSearcher(searchPort, props, reranker);
    }

    private static XhsNoteCandidate candidate(String id, String title, String noteUrl) {
        return new XhsNoteCandidate(id, "note-" + id, title, "desc", noteUrl, "1", "author");
    }

    private static XhsNoteSearchHit hitHttps(String title, int index) {
        return new XhsNoteSearchHit(
                "note-" + index,
                title,
                "desc",
                "https://www.xiaohongshu.com/explore/" + index,
                "100",
                "author");
    }
}

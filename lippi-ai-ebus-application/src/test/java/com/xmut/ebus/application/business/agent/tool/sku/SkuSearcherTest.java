package com.xmut.ebus.application.business.agent.tool.sku;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkuSearcherTest {

    @Mock
    private SkuSearchPort port;

    private SkuSearchProperties props;
    private SkuSearcher searcher;

    @BeforeEach
    void setUp() {
        props = new SkuSearchProperties();
        searcher = newSearcher(port, SkuReranker.identity());
    }

    @Test
    void expandQuery_trimsToSingleton() {
        SkuSearcher s = newSearcher(port, SkuReranker.identity());
        assertEquals(Collections.singletonList("杯垫"), s.expandQuery(" 杯垫 "));
        assertTrue(s.expandQuery("  ").isEmpty());
    }

    @Test
    void pooling_capsAtRerankPoolSize() {
        props.getSearcher().setRerankPoolSize(2);
        List<SkuCandidate> in = Arrays.asList(c("h1"), c("h2"), c("h3"));
        assertEquals(2, newSearcher(port, SkuReranker.identity()).pooling(in).size());
    }

    @Test
    void doCheck_dropsNonHttpsAndBlankTitle() {
        List<SkuCandidate> out = searcher.doCheck(Arrays.asList(
                candidate("h1", "ok", "https://a.com/1"),
                candidate("h2", "", "https://a.com/2"),
                candidate("h3", "x", "http://insecure")));
        assertEquals(1, out.size());
        assertEquals("h1", out.get(0).getId());
    }

    @Test
    void search_demo_callsPortOnce_andRespectsPageSize() {
        when(port.search(eq("q"), anyString(), anyInt()))
                .thenReturn(Arrays.asList(hitHttps("A"), hitHttps("B"), hitHttps("C")));
        List<SkuSearchHit> out = searcher.search("q", "taobao_tbk", 2);
        verify(port, times(1)).search(eq("q"), anyString(), anyInt());
        assertEquals(2, out.size());
    }

    @Test
    void search_disabled_bypassToPortPageSize() {
        props.getSearcher().setEnabled(false);
        when(port.search(eq("q"), eq("p"), eq(5))).thenReturn(Collections.singletonList(hitHttps("A")));
        assertEquals(1, searcher.search("q", "p", 5).size());
        verify(port).search("q", "p", 5);
    }

    private SkuSearcher newSearcher(SkuSearchPort searchPort, SkuReranker reranker) {
        return new SkuSearcher(searchPort, props, reranker);
    }

    private static SkuCandidate c(String id) {
        return new SkuCandidate(id, "taobao_tbk", "title-" + id, "1", "cat", "https://example.com/" + id, "ref");
    }

    private static SkuCandidate candidate(String id, String title, String detailUrl) {
        return new SkuCandidate(id, "taobao_tbk", title, "1", "cat", detailUrl, "ref");
    }

    private static SkuSearchHit hitHttps(String title) {
        return new SkuSearchHit("taobao_tbk", title, "1", "cat", "https://example.com/x", "ref");
    }
}

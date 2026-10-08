package com.xmut.forma.extension.tool.common.web.crawl.port;

import com.xmut.forma.extension.tool.web.port.WebFetchHit;
import java.util.List;

/**
 * 爬取 atom: bounded multi-page / same-site traversal.
 * Near-term stub only — not wired to business tools or Skills.
 */
public interface SiteCrawlPort {

    /**
     * Crawl starting at {@code startUrl}, up to {@code maxPages} pages.
     *
     * @param startUrl public https start URL
     * @param maxPages upper bound on pages to visit ({@code >1} for real crawl)
     * @return page hits in visit order
     */
    List<WebFetchHit> crawl(String startUrl, int maxPages);
}

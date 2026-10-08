package com.xmut.forma.extension.tool.common.web.crawl;

import com.xmut.forma.extension.tool.common.web.crawl.port.SiteCrawlPort;
import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;
import java.util.List;

/**
 * Placeholder {@link SiteCrawlPort} — not wired to any business path yet.
 */
public final class UnsupportedSiteCrawlPort implements SiteCrawlPort {

    @Override
    public List<WebFetchHit> crawl(String startUrl, int maxPages) {
        throw new UnsupportedOperationException("SiteCrawlPort not wired yet");
    }
}

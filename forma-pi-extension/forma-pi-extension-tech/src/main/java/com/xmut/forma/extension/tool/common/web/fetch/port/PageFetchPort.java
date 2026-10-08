package com.xmut.forma.extension.tool.common.web.fetch.port;

import com.xmut.forma.extension.tool.common.web.fetch.port.WebFetchHit;

/**
 * 抓取 atom: one public URL → one page of visible text.
 * Evolved from {@code WebFetchPort}; not Agent-visible — consumed by business tools.
 */
public interface PageFetchPort {

    WebFetchHit fetch(String url);
}

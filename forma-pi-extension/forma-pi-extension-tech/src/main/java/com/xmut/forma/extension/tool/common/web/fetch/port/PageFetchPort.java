package com.xmut.forma.extension.tool.common.web.fetch.port;

import com.xmut.forma.extension.tool.web.port.WebFetchHit;

/**
 * 抓取 atom: one public URL → one page of visible text.
 * Evolved from {@code WebFetchPort}; Agent still sees {@code fetch_web_page} until later tasks.
 */
public interface PageFetchPort {

    WebFetchHit fetch(String url);
}

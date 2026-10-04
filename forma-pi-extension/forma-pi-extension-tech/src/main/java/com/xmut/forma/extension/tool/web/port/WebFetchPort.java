package com.xmut.forma.extension.tool.web.port;

/**
 * Read-only public page fetch (Mock / Apify website-content-crawler).
 */
public interface WebFetchPort {

    WebFetchHit fetch(String url);
}

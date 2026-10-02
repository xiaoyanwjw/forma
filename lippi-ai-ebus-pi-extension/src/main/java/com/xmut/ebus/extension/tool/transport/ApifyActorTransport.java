package com.xmut.ebus.extension.tool.transport;

/**
 * HTTP transport for Apify Actor sync dataset API (mockable in tests).
 */
public interface ApifyActorTransport {

    /**
     * @param actorIdSlash e.g. {@code zen-studio/taobao-search-scraper}
     * @param jsonBody     Actor input JSON
     * @return raw response body (dataset items JSON array or error object)
     */
    String post(String actorIdSlash, String token, long timeoutMs, String jsonBody);
}

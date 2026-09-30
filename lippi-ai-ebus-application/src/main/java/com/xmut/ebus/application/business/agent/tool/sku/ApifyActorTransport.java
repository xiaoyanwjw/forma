package com.xmut.ebus.application.business.agent.tool.sku;

/**
 * HTTP transport for Apify Actor sync dataset API (mockable in tests).
 */
public interface ApifyActorTransport {

    /**
     * @param actorIdSlash e.g. {@code zen-studio/taobao-search-scraper}
     * @param jsonBody     Actor input JSON
     * @return raw response body (dataset items JSON array or error object)
     */
    String postSyncDatasetItems(String actorIdSlash, String token, long timeoutMs, String jsonBody);
}

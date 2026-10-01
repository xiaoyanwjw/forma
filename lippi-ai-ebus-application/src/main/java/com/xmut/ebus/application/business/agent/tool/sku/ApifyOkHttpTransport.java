package com.xmut.ebus.application.business.agent.tool.sku;

import com.xmut.ebus.common.http.RestClient;
import com.xmut.ebus.common.http.RestClientException;

/**
 * Apify Actor REST transport backed by shared {@link RestClient} (OkHttp pool).
 */
public final class ApifyOkHttpTransport implements ApifyActorTransport {

    private static final String API_BASE = "https://api.apify.com/v2/acts/";

    private final RestClient restClient;

    public ApifyOkHttpTransport() {
        this(RestClient.createDefault());
    }

    public ApifyOkHttpTransport(RestClient restClient) {
        this.restClient = restClient == null ? RestClient.createDefault() : restClient;
    }

    @Override
    public String post(String actorIdSlash, String token, long timeoutMs, String jsonBody) {
        String actorPath = actorIdSlash.replace('/', '~');
        long timeoutSeconds = Math.max(1L, (timeoutMs + 999L) / 1000L);
        String url = API_BASE + actorPath + "/run-sync-get-dataset-items?timeout=" + timeoutSeconds;
        try {
            return restClient.postJson(
                    url,
                    jsonBody,
                    RestClient.bearerJsonHeaders(token),
                    timeoutMs);
        } catch (RestClientException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}

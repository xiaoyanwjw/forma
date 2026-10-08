package com.xmut.forma.extension.tool.common.web.fetch.port;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forma.web-fetch.*} — public page fetch client (mock | apify) and Apify actor settings.
 */
@ConfigurationProperties(prefix = "forma.web-fetch")
public class WebFetchProperties {

    /** apify（默认）或 mock */
    private String client = "apify";

    private final Apify apify = new Apify();

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public Apify getApify() {
        return apify;
    }

    public static class Apify {
        private String actorId = "apify/website-content-crawler";
        private long timeoutMs = 120_000L;
        /** Optional; bind via {@code APIFY_TOKEN} or {@code forma.web-fetch.apify.token}. */
        private String token;

        public String getActorId() {
            return actorId;
        }

        public void setActorId(String actorId) {
            this.actorId = actorId;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}

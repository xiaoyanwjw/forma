package com.xmut.ebus.extension.tool.xhs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ebus.xhs-note-fetch.*} — XHS note detail client (mock | apify) and Apify actor settings.
 */
@ConfigurationProperties(prefix = "ebus.xhs-note-fetch")
public class XhsNoteFetchProperties {

    /** mock（默认）或 apify */
    private String client = "mock";

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
        private String actorId = "khadinakbar/xiaohongshu-note-detail-scraper";
        private long timeoutMs = 120_000L;
        /** Optional; bind via {@code APIFY_TOKEN} or {@code ebus.xhs-note-fetch.apify.token}. */
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

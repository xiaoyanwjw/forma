package com.xmut.forma.extension.tool.ph.port;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forma.product-launch-search.*} — Product Hunt list Actor via Apify.
 *
 * <p>Default Actor id is nailed here: {@link #DEFAULT_ACTOR_ID}
 * ({@code cloud9_ai/producthunt-scraper}). Override with {@code FORMA_TECH_PH_ACTOR_ID}.
 * Plan placeholder {@code curious_coder/producthunt-scraper} 404'd on Apify Store (2026-10-08).
 */
@ConfigurationProperties(prefix = "forma.product-launch-search")
public class ProductLaunchSearchProperties {

    /**
     * Public Product Hunt launches scraper (searchQuery / timeFrame / maxResults).
     * Override: {@code FORMA_TECH_PH_ACTOR_ID} or {@code forma.product-launch-search.apify.actor-id}.
     */
    public static final String DEFAULT_ACTOR_ID = "cloud9_ai/producthunt-scraper";

    private final Apify apify = new Apify();

    public Apify getApify() {
        return apify;
    }

    public static class Apify {
        private String actorId = DEFAULT_ACTOR_ID;
        private long timeoutMs = 120_000L;
        /** Optional; bind via {@code APIFY_TOKEN} or {@code forma.product-launch-search.apify.token}. */
        private String token;
        /**
         * Optional Product Hunt Developer Token when the Actor requires it
         * ({@code PRODUCT_HUNT_TOKEN} / {@code forma.product-launch-search.apify.product-hunt-token}).
         */
        private String productHuntToken;

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

        public String getProductHuntToken() {
            return productHuntToken;
        }

        public void setProductHuntToken(String productHuntToken) {
            this.productHuntToken = productHuntToken;
        }
    }
}

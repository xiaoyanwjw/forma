package com.xmut.forma.extension.tool.product.recall.source.ph.port;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forma.product-launch-search.*} — Product Hunt list Actor via Apify.
 *
 * <p>Default Actor id is nailed here: {@link #DEFAULT_ACTOR_ID}
 * ({@code cazadores/product-hunt-scraper}). Override with {@code FORMA_TECH_PH_ACTOR_ID}.
 */
@ConfigurationProperties(prefix = "forma.product-launch-search")
public class ProductLaunchSearchProperties {

    /**
     * Daily leaderboard scraper (mode=leaderboard, startDate=PH Pacific day).
     * Override: {@code FORMA_TECH_PH_ACTOR_ID} or {@code forma.product-launch-search.apify.actor-id}.
     */
    public static final String DEFAULT_ACTOR_ID = "cazadores/product-hunt-scraper";

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

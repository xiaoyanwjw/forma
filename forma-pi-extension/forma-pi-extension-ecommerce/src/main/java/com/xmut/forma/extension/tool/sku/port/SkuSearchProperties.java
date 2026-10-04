package com.xmut.forma.extension.tool.sku.port;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code forma.sku-search.*} — SKU search client (mock | apify) and Apify actor settings.
 */
@ConfigurationProperties(prefix = "forma.sku-search")
public class SkuSearchProperties {

    /** apify（默认）或 mock */
    private String client = "apify";

    private final Apify apify = new Apify();

    private final Searcher searcher = new Searcher();

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public Apify getApify() {
        return apify;
    }

    public Searcher getSearcher() {
        return searcher;
    }

    /**
     * {@code forma.sku-search.searcher.*}
     */
    public static class Searcher {
        private boolean enabled = true;
        private int maxLegs = 1;
        private int sourcePageSize = 20;
        private int rerankPoolSize = 40;
        private String rerankUseCase = "forma.sku.rerank";
        private long rerankTimeoutMs = 12_000L;
        private boolean exposeReasons = false;
        /** Comma-separated; empty means no filter. */
        private String bannedTitleKeywords = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxLegs() {
            return maxLegs;
        }

        public void setMaxLegs(int maxLegs) {
            this.maxLegs = maxLegs;
        }

        public int getSourcePageSize() {
            return sourcePageSize;
        }

        public void setSourcePageSize(int sourcePageSize) {
            this.sourcePageSize = sourcePageSize;
        }

        public int getRerankPoolSize() {
            return rerankPoolSize;
        }

        public void setRerankPoolSize(int rerankPoolSize) {
            this.rerankPoolSize = rerankPoolSize;
        }

        public String getRerankUseCase() {
            return rerankUseCase;
        }

        public void setRerankUseCase(String rerankUseCase) {
            this.rerankUseCase = rerankUseCase;
        }

        public long getRerankTimeoutMs() {
            return rerankTimeoutMs;
        }

        public void setRerankTimeoutMs(long rerankTimeoutMs) {
            this.rerankTimeoutMs = rerankTimeoutMs;
        }

        public boolean isExposeReasons() {
            return exposeReasons;
        }

        public void setExposeReasons(boolean exposeReasons) {
            this.exposeReasons = exposeReasons;
        }

        public String getBannedTitleKeywords() {
            return bannedTitleKeywords;
        }

        public void setBannedTitleKeywords(String bannedTitleKeywords) {
            this.bannedTitleKeywords = bannedTitleKeywords;
        }
    }

    public static class Apify {
        private String actorId = "zen-studio/taobao-search-scraper";
        private long timeoutMs = 120_000L;
        /** Optional; bind via {@code APIFY_TOKEN} or {@code forma.sku-search.apify.token}. */
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

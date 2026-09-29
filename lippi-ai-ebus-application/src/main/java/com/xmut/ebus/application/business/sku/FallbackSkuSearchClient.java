package com.xmut.ebus.application.business.sku;

import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Delegates to a primary {@link SkuSearchPort}; on failure or zero hits, falls back to mock search.
 */
public final class FallbackSkuSearchClient implements SkuSearchPort {

    private static final Logger log = LoggerFactory.getLogger(FallbackSkuSearchClient.class);

    private final SkuSearchPort primary;
    private final SkuSearchPort fallback;
    private final String actorId;

    public FallbackSkuSearchClient(SkuSearchPort primary, SkuSearchPort fallback, String actorId) {
        this.primary = primary;
        this.fallback = fallback;
        this.actorId = actorId == null ? "" : actorId;
    }

    @Override
    public List<SkuSearchHit> search(String query, String platform, int pageSize) {
        try {
            List<SkuSearchHit> hits = primary.search(query, platform, pageSize);
            if (hits == null || hits.isEmpty()) {
                logFallback("empty_hits", query, 0, null);
                return fallback.search(query, platform, pageSize);
            }
            return hits;
        } catch (RuntimeException e) {
            String reason = classifyReason(e);
            logFallback(reason, query, 0, e);
            return fallback.search(query, platform, pageSize);
        }
    }

    private static String classifyReason(RuntimeException e) {
        String message = e.getMessage();
        if (message != null) {
            if ("missing_token".equals(message) || message.contains("missing_token")) {
                return "missing_token";
            }
            String lower = message.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) {
                return "timeout";
            }
            if (lower.contains("http_error")) {
                return "http_error";
            }
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            String lower = cause.getMessage().toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) {
                return "timeout";
            }
        }
        return "http_error";
    }

    private void logFallback(String reason, String query, int hitCount, RuntimeException e) {
        int queryLen = query == null ? 0 : query.length();
        NameValue<?>[] args = new NameValue<?>[] {
                NameValue.create("client", "apify"),
                NameValue.create("actorId", actorId),
                NameValue.create("queryLen", queryLen),
                NameValue.create("hitCount", hitCount)
        };
        if (e != null) {
            LoggerUtils.error(log, FallbackSkuSearchClient.class, "search", reason, e, args);
        } else {
            LoggerUtils.error(log, FallbackSkuSearchClient.class, "search", reason, args);
        }
    }
}

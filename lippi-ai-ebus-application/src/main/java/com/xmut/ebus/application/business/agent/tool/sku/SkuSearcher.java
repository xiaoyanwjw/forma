package com.xmut.ebus.application.business.agent.tool.sku;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Demo SKU search pipeline: expand → search → check → pool → rerank → top hits.
 */
public final class SkuSearcher {

    private static final Logger log = LoggerFactory.getLogger(SkuSearcher.class);

    private final SkuSearchPort skuSearchPort;
    private final SkuSearchProperties properties;
    private final SkuReranker skuReranker;

    public SkuSearcher(SkuSearchPort skuSearchPort,
                       SkuSearchProperties properties,
                       SkuReranker skuReranker) {
        this.skuSearchPort = skuSearchPort;
        this.properties = properties;
        this.skuReranker = skuReranker;
    }

    public List<SkuSearchHit> search(String query, String platform, int pageSize) {
        SkuSearchProperties.Searcher searcher = properties.getSearcher();
        if (!searcher.isEnabled()) {
            List<SkuSearchHit> hits = skuSearchPort.search(query, platform, pageSize);
            if (hits == null) {
                return Collections.emptyList();
            }
            return hits;
        }
        List<String> queries = expandQuery(query);
        List<SkuCandidate> raw = doSearch(queries, platform, searcher.getSourcePageSize());
        List<SkuCandidate> checked = doCheck(raw);
        List<SkuCandidate> pool = pooling(checked);
        String intent = query != null ? query.trim() : "";
        List<SkuCandidate> ranked = rerank(intent, pool);
        return topHits(ranked, pageSize);
    }

    public List<String> expandQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(query.trim());
    }

    public List<SkuCandidate> doSearch(List<String> queries, String platform, int sourcePageSize) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkuSearchHit> hits = skuSearchPort.search(queries.get(0), platform, sourcePageSize);
        return toCandidates(hits, "L0");
    }

    public List<SkuCandidate> doCheck(List<SkuCandidate> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> banned = parseBannedKeywords(properties.getSearcher().getBannedTitleKeywords());
        List<SkuCandidate> out = new ArrayList<SkuCandidate>(hits.size());
        for (SkuCandidate hit : hits) {
            if (hit == null) {
                continue;
            }
            if (!StringUtils.hasText(hit.getTitle())) {
                continue;
            }
            if (!isHttpsDetailUrl(hit.getDetailUrl())) {
                continue;
            }
            if (containsBannedKeyword(hit.getTitle(), banned)) {
                continue;
            }
            out.add(hit);
        }
        return out;
    }

    public List<SkuCandidate> pooling(List<SkuCandidate> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = properties.getSearcher().getRerankPoolSize();
        if (cap <= 0 || hits.size() <= cap) {
            return new ArrayList<SkuCandidate>(hits);
        }
        return new ArrayList<SkuCandidate>(hits.subList(0, cap));
    }

    public List<SkuCandidate> rerank(String intent, List<SkuCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> ids = skuReranker.orderIds(intent, pool);
            if (ids == null || ids.isEmpty()) {
                return pool;
            }
            return reorderByIds(pool, ids);
        } catch (Exception ex) {
            log.warn("sku rerank failed: {}", ex.toString());
            return pool;
        }
    }

    public List<SkuSearchHit> topHits(List<SkuCandidate> ranked, int pageSize) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = pageSize > 0 ? pageSize : ranked.size();
        List<SkuSearchHit> out = new ArrayList<SkuSearchHit>(Math.min(limit, ranked.size()));
        for (int i = 0; i < ranked.size() && out.size() < limit; i++) {
            SkuCandidate c = ranked.get(i);
            if (c == null) {
                continue;
            }
            out.add(toSearchHit(c));
        }
        return out;
    }

    private static List<SkuCandidate> toCandidates(List<SkuSearchHit> hits, String recallFrom) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkuCandidate> out = new ArrayList<SkuCandidate>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            SkuSearchHit hit = hits.get(i);
            if (hit == null) {
                continue;
            }
            String id = "h" + (i + 1);
            out.add(new SkuCandidate(
                    id,
                    hit.getPlatform(),
                    hit.getTitle(),
                    hit.getPrice(),
                    hit.getCategory(),
                    hit.getDetailUrl(),
                    hit.getRawRef(),
                    recallFrom));
        }
        return out;
    }

    private static SkuSearchHit toSearchHit(SkuCandidate c) {
        return new SkuSearchHit(
                c.getPlatform(),
                c.getTitle(),
                c.getPrice(),
                c.getCategory(),
                c.getDetailUrl(),
                c.getRawRef());
    }

    private static List<SkuCandidate> reorderByIds(List<SkuCandidate> pool, List<String> ids) {
        Map<String, SkuCandidate> byId = new HashMap<String, SkuCandidate>();
        for (SkuCandidate c : pool) {
            if (c != null && c.getId() != null) {
                byId.put(c.getId(), c);
            }
        }
        List<SkuCandidate> ordered = new ArrayList<SkuCandidate>(pool.size());
        Set<String> seen = new HashSet<String>();
        for (String id : ids) {
            if (id == null || seen.contains(id)) {
                continue;
            }
            SkuCandidate c = byId.get(id);
            if (c == null) {
                continue;
            }
            ordered.add(c);
            seen.add(id);
        }
        for (SkuCandidate c : pool) {
            if (c == null || c.getId() == null || seen.contains(c.getId())) {
                continue;
            }
            ordered.add(c);
            seen.add(c.getId());
        }
        return ordered;
    }

    private static boolean isHttpsDetailUrl(String detailUrl) {
        if (!StringUtils.hasText(detailUrl)) {
            return false;
        }
        return detailUrl.trim().toLowerCase(Locale.ROOT).startsWith("https:");
    }

    private static List<String> parseBannedKeywords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",");
        List<String> banned = new ArrayList<String>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                banned.add(trimmed);
            }
        }
        return banned;
    }

    private static boolean containsBannedKeyword(String title, List<String> banned) {
        if (banned == null || banned.isEmpty() || title == null) {
            return false;
        }
        for (String keyword : banned) {
            if (keyword != null && title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

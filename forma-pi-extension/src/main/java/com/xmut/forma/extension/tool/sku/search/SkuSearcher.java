package com.xmut.forma.extension.tool.sku.search;

import com.xmut.forma.extension.tool.sku.port.SkuSearchHit;
import com.xmut.forma.extension.tool.sku.port.SkuSearchPort;
import com.xmut.forma.extension.tool.sku.port.SkuSearchProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Demo SKU search pipeline: expand → search → check → pool → rerank → top hits.
 */
public class SkuSearcher {

    private static final Logger log = LoggerFactory.getLogger(SkuSearcher.class);

    /** Demo fixed marketplace for {@link SkuSearchPort}; not part of the public search API. */
    static final String DEFAULT_PLATFORM = "taobao_tbk";

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

    public List<SkuSearchHit> search(String query, int pageSize) {
        SkuSearchProperties.Searcher searcher = properties.getSearcher();
        if (!searcher.isEnabled()) {
            List<SkuSearchHit> hits = skuSearchPort.search(query, DEFAULT_PLATFORM, pageSize);
            if (hits == null) {
                return Collections.emptyList();
            }
            return hits;
        }
        List<String> queries = expandQuery(query);
        List<SkuCandidate> raw = doSearch(queries, pageSize);
        List<SkuCandidate> checked = doCheck(raw);
        List<SkuCandidate> pool = pooling(checked);
        List<SkuCandidate> ranked = rerank(query, pool);
        return topHits(ranked, pageSize);
    }

    public List<String> expandQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(query.trim());
    }

    public List<SkuCandidate> doSearch(List<String> queries, int pageSize) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkuSearchHit> hits = skuSearchPort.search(queries.get(0), DEFAULT_PLATFORM, pageSize);
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

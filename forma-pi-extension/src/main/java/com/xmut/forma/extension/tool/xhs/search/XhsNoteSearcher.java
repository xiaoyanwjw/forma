package com.xmut.forma.extension.tool.xhs.search;

import com.xmut.forma.extension.tool.xhs.port.XhsNoteSearchHit;
import com.xmut.forma.extension.tool.xhs.port.XhsNoteSearchPort;
import com.xmut.forma.extension.tool.xhs.port.XhsNoteSearchProperties;
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
 * Demo XHS note search pipeline: expand → search → check → pool → rerank → top hits.
 */
public class XhsNoteSearcher {

    private static final Logger log = LoggerFactory.getLogger(XhsNoteSearcher.class);

    private final XhsNoteSearchPort xhsNoteSearchPort;
    private final XhsNoteSearchProperties properties;
    private final XhsNoteReranker xhsNoteReranker;

    public XhsNoteSearcher(XhsNoteSearchPort xhsNoteSearchPort,
                           XhsNoteSearchProperties properties,
                           XhsNoteReranker xhsNoteReranker) {
        this.xhsNoteSearchPort = xhsNoteSearchPort;
        this.properties = properties;
        this.xhsNoteReranker = xhsNoteReranker;
    }

    public List<XhsNoteSearchHit> search(String query, int pageSize) {
        XhsNoteSearchProperties.Searcher searcher = properties.getSearcher();
        if (!searcher.isEnabled()) {
            List<XhsNoteSearchHit> hits = xhsNoteSearchPort.search(query, pageSize);
            if (hits == null) {
                return Collections.emptyList();
            }
            return hits;
        }
        List<String> queries = expandQuery(query);
        List<XhsNoteCandidate> raw = doSearch(queries, pageSize);
        List<XhsNoteCandidate> checked = doCheck(raw);
        List<XhsNoteCandidate> pool = pooling(checked);
        List<XhsNoteCandidate> ranked = rerank(query, pool);
        return topHits(ranked, pageSize);
    }

    public List<String> expandQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(query.trim());
    }

    public List<XhsNoteCandidate> doSearch(List<String> queries, int pageSize) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        List<XhsNoteSearchHit> hits = xhsNoteSearchPort.search(queries.get(0), pageSize);
        return toCandidates(hits, "L0");
    }

    public List<XhsNoteCandidate> doCheck(List<XhsNoteCandidate> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> banned = parseBannedKeywords(properties.getSearcher().getBannedTitleKeywords());
        List<XhsNoteCandidate> out = new ArrayList<XhsNoteCandidate>(hits.size());
        for (XhsNoteCandidate hit : hits) {
            if (hit == null) {
                continue;
            }
            if (!StringUtils.hasText(hit.getTitle())) {
                continue;
            }
            if (!isHttpsNoteUrl(hit.getNoteUrl())) {
                continue;
            }
            if (containsBannedKeyword(hit.getTitle(), banned)) {
                continue;
            }
            out.add(hit);
        }
        return out;
    }

    public List<XhsNoteCandidate> pooling(List<XhsNoteCandidate> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = properties.getSearcher().getRerankPoolSize();
        if (cap <= 0 || hits.size() <= cap) {
            return new ArrayList<XhsNoteCandidate>(hits);
        }
        return new ArrayList<XhsNoteCandidate>(hits.subList(0, cap));
    }

    public List<XhsNoteCandidate> rerank(String intent, List<XhsNoteCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> ids = xhsNoteReranker.orderIds(intent, pool);
            if (ids == null || ids.isEmpty()) {
                return pool;
            }
            return reorderByIds(pool, ids);
        } catch (Exception ex) {
            log.warn("xhs note rerank failed: {}", ex.toString());
            return pool;
        }
    }

    public List<XhsNoteSearchHit> topHits(List<XhsNoteCandidate> ranked, int pageSize) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = pageSize > 0 ? pageSize : ranked.size();
        List<XhsNoteSearchHit> out = new ArrayList<XhsNoteSearchHit>(Math.min(limit, ranked.size()));
        for (int i = 0; i < ranked.size() && out.size() < limit; i++) {
            XhsNoteCandidate c = ranked.get(i);
            if (c == null) {
                continue;
            }
            out.add(toSearchHit(c));
        }
        return out;
    }

    private static List<XhsNoteCandidate> toCandidates(List<XhsNoteSearchHit> hits, String recallFrom) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<XhsNoteCandidate> out = new ArrayList<XhsNoteCandidate>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            XhsNoteSearchHit hit = hits.get(i);
            if (hit == null) {
                continue;
            }
            String id = "h" + (i + 1);
            out.add(new XhsNoteCandidate(
                    id,
                    hit.getNoteId(),
                    hit.getTitle(),
                    hit.getDesc(),
                    hit.getNoteUrl(),
                    hit.getLikedCount(),
                    hit.getAuthor(),
                    recallFrom));
        }
        return out;
    }

    private static XhsNoteSearchHit toSearchHit(XhsNoteCandidate c) {
        return new XhsNoteSearchHit(
                c.getNoteId(),
                c.getTitle(),
                c.getDesc(),
                c.getNoteUrl(),
                c.getLikedCount(),
                c.getAuthor());
    }

    private static List<XhsNoteCandidate> reorderByIds(List<XhsNoteCandidate> pool, List<String> ids) {
        Map<String, XhsNoteCandidate> byId = new HashMap<String, XhsNoteCandidate>();
        for (XhsNoteCandidate c : pool) {
            if (c != null && c.getId() != null) {
                byId.put(c.getId(), c);
            }
        }
        List<XhsNoteCandidate> ordered = new ArrayList<XhsNoteCandidate>(pool.size());
        Set<String> seen = new HashSet<String>();
        for (String id : ids) {
            if (id == null || seen.contains(id)) {
                continue;
            }
            XhsNoteCandidate c = byId.get(id);
            if (c == null) {
                continue;
            }
            ordered.add(c);
            seen.add(id);
        }
        for (XhsNoteCandidate c : pool) {
            if (c == null || c.getId() == null || seen.contains(c.getId())) {
                continue;
            }
            ordered.add(c);
            seen.add(c.getId());
        }
        return ordered;
    }

    private static boolean isHttpsNoteUrl(String noteUrl) {
        if (!StringUtils.hasText(noteUrl)) {
            return false;
        }
        return noteUrl.trim().toLowerCase(Locale.ROOT).startsWith("https:");
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

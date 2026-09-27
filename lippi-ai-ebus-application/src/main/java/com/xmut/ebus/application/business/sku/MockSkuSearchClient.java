package com.xmut.ebus.application.business.sku;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fixed 10-row mock catalog. Every hit has an https {@code detailUrl}.
 */
public final class MockSkuSearchClient implements SkuSearchPort {

    static final int SAMPLE_COUNT = 10;
    static final String DETAIL_URL_PREFIX = "https://mock.tbk.local/item/";

    @Override
    public List<SkuSearchHit> search(String query, String platform, int pageSize) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String q = query.trim();
        String plat = StringUtils.hasText(platform) ? platform.trim() : "taobao_tbk";
        int limit = pageSize < 1 ? 1 : Math.min(pageSize, SAMPLE_COUNT);
        List<SkuSearchHit> hits = new ArrayList<SkuSearchHit>(limit);
        for (int i = 1; i <= limit; i++) {
            hits.add(new SkuSearchHit(
                    plat,
                    q + " 候选样例 " + i,
                    String.valueOf(19 + i) + ".90",
                    "家居香氛",
                    DETAIL_URL_PREFIX + i,
                    "mock-" + i));
        }
        return Collections.unmodifiableList(hits);
    }
}

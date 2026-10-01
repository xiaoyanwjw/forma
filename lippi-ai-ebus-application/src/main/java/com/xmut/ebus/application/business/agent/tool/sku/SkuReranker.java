package com.xmut.ebus.application.business.agent.tool.sku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orders candidate ids for {@link SkuSearcher#rerank(String, List)}.
 */
public interface SkuReranker {

    List<String> orderIds(String intent, List<SkuCandidate> pool);

    static SkuReranker identity() {
        return new SkuReranker() {
            @Override
            public List<String> orderIds(String intent, List<SkuCandidate> pool) {
                if (pool == null || pool.isEmpty()) {
                    return Collections.emptyList();
                }
                List<String> ids = new ArrayList<String>(pool.size());
                for (SkuCandidate c : pool) {
                    ids.add(c.getId());
                }
                return ids;
            }
        };
    }
}

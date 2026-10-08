package com.xmut.forma.extension.tool.product.recall.source.ph.port;

import java.util.List;

/**
 * Fetches Product Hunt launch rows (raw mapped candidates; filter/dedupe in the tool handler).
 */
public interface ProductLaunchSearchPort {

    /**
     * @param topic      search topic (passed to Actor when supported)
     * @param fetchLimit max rows to request from Actor (≥1)
     */
    List<ProductLaunchCandidate> search(String topic, int fetchLimit);
}

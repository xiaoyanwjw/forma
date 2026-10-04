package com.xmut.forma.extension.tool.sku.port;

import java.util.List;

/**
 * Read-only SKU search (Mock / Apify).
 */
public interface SkuSearchPort {

    List<SkuSearchHit> search(String query, String platform, int pageSize);
}

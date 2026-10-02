package com.xmut.ebus.extension.tool.sku.port;

import java.util.List;

/**
 * Read-only SKU search (Mock / Apify).
 */
public interface SkuSearchPort {

    List<SkuSearchHit> search(String query, String platform, int pageSize);
}

package com.xmut.ebus.application.business.sku;

import java.util.List;

/**
 * Read-only SKU search (Mock / Apify).
 */
public interface SkuSearchPort {

    List<SkuSearchHit> search(String query, String platform, int pageSize);
}

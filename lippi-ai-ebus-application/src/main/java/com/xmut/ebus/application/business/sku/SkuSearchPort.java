package com.xmut.ebus.application.business.sku;

import java.util.List;

/**
 * Read-only SKU search port. Real TBK client is Task 6.
 */
public interface SkuSearchPort {

    List<SkuSearchHit> search(String query, String platform, int pageSize);
}

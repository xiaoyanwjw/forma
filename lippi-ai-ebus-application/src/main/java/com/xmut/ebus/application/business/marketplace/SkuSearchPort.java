package com.xmut.ebus.application.business.marketplace;

import java.util.List;

/**
 * Read-only marketplace search port. Real TBK client is Task 6.
 */
public interface SkuSearchPort {

    List<SkuSearchHit> search(String query, String platform, int pageSize);
}

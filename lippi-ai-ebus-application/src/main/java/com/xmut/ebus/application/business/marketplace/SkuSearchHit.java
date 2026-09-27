package com.xmut.ebus.application.business.marketplace;

/**
 * Immutable marketplace SKU summary returned by {@link SkuSearchPort}.
 */
public final class SkuSearchHit {

    private final String platform;
    private final String title;
    private final String price;
    private final String category;
    private final String detailUrl;
    private final String rawRef;

    public SkuSearchHit(String platform,
                        String title,
                        String price,
                        String category,
                        String detailUrl,
                        String rawRef) {
        this.platform = platform;
        this.title = title;
        this.price = price;
        this.category = category;
        this.detailUrl = detailUrl;
        this.rawRef = rawRef;
    }

    public String getPlatform() {
        return platform;
    }

    public String getTitle() {
        return title;
    }

    public String getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public String getRawRef() {
        return rawRef;
    }
}

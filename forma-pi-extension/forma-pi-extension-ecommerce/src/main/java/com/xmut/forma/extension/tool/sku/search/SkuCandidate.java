package com.xmut.forma.extension.tool.sku.search;

/**
 * Internal SKU candidate with a short id ({@code h1}…) for reranking.
 */
public final class SkuCandidate {

    private final String id;
    private final String platform;
    private final String title;
    private final String price;
    private final String category;
    private final String detailUrl;
    private final String rawRef;
    private final String recallFrom;

    public SkuCandidate(String id,
                        String platform,
                        String title,
                        String price,
                        String category,
                        String detailUrl,
                        String rawRef) {
        this(id, platform, title, price, category, detailUrl, rawRef, null);
    }

    public SkuCandidate(String id,
                        String platform,
                        String title,
                        String price,
                        String category,
                        String detailUrl,
                        String rawRef,
                        String recallFrom) {
        this.id = id;
        this.platform = platform;
        this.title = title;
        this.price = price;
        this.category = category;
        this.detailUrl = detailUrl;
        this.rawRef = rawRef;
        this.recallFrom = recallFrom;
    }

    public String getId() {
        return id;
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

    public String getRecallFrom() {
        return recallFrom;
    }
}

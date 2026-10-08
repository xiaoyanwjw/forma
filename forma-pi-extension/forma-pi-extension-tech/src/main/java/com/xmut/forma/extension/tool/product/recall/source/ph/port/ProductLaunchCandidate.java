package com.xmut.forma.extension.tool.product.recall.source.ph.port;

/**
 * Normalized product launch candidate for {@code recall_products}.
 */
public final class ProductLaunchCandidate {

    private final String title;
    private final String tagline;
    private final String url;
    private final Integer votes;
    private final String publishedAt;
    private final String source;

    public ProductLaunchCandidate(String title,
                                  String tagline,
                                  String url,
                                  Integer votes,
                                  String publishedAt,
                                  String source) {
        this.title = title;
        this.tagline = tagline;
        this.url = url;
        this.votes = votes;
        this.publishedAt = publishedAt;
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public String getTagline() {
        return tagline;
    }

    public String getUrl() {
        return url;
    }

    public Integer getVotes() {
        return votes;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getSource() {
        return source;
    }
}

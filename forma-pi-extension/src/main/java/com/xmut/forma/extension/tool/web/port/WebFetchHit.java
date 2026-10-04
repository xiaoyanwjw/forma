package com.xmut.forma.extension.tool.web.port;

/**
 * One public page extracted by {@link WebFetchPort}. {@code text} stays out of tool JSON.
 */
public final class WebFetchHit {

    private final String finalUrl;
    private final String title;
    private final String text;
    private final boolean truncated;

    public WebFetchHit(String finalUrl, String title, String text, boolean truncated) {
        this.finalUrl = finalUrl;
        this.title = title;
        this.text = text;
        this.truncated = truncated;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public boolean isTruncated() {
        return truncated;
    }
}

package com.xmut.ebus.extension.tool.xhs.port;

import java.util.Collections;
import java.util.List;

/**
 * Single Xiaohongshu note detail returned by {@link XhsNoteFetchPort}.
 */
public final class XhsNoteFetchHit {

    private final String title;
    private final String body;
    private final String noteUrl;
    private final String author;
    private final List<String> tags;

    public XhsNoteFetchHit(String title,
                           String body,
                           String noteUrl,
                           String author,
                           List<String> tags) {
        this.title = title;
        this.body = body;
        this.noteUrl = noteUrl;
        this.author = author;
        this.tags = tags == null ? Collections.<String>emptyList() : Collections.unmodifiableList(tags);
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getNoteUrl() {
        return noteUrl;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getTags() {
        return tags;
    }
}

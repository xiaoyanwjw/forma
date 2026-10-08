package com.xmut.forma.extension.tool.common.excerpt;

/**
 * One slice of source markdown prepared for excerpt extraction.
 */
public final class TechDigestChunk {

    private final String heading;
    private final String text;

    public TechDigestChunk(String heading, String text) {
        this.heading = heading == null ? "" : heading;
        this.text = text == null ? "" : text;
    }

    public String getHeading() {
        return heading;
    }

    public String getText() {
        return text;
    }
}

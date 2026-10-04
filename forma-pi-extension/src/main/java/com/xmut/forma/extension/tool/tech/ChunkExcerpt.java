package com.xmut.forma.extension.tool.tech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Heading plus the quotes taken from one source chunk.
 * Quotes are raw model output until {@link ExcerptQuoteGuard} runs.
 */
public final class ChunkExcerpt {

    private final String heading;
    private final List<String> quotes;

    public ChunkExcerpt(String heading, List<String> quotes) {
        this.heading = heading == null ? "" : heading;
        if (quotes == null || quotes.isEmpty()) {
            this.quotes = Collections.emptyList();
        } else {
            this.quotes = Collections.unmodifiableList(new ArrayList<String>(quotes));
        }
    }

    public String getHeading() {
        return heading;
    }

    public List<String> getQuotes() {
        return quotes;
    }
}

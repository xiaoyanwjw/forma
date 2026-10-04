package com.xmut.forma.extension.tool.tech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Drops quotes that are not continuous substrings of the chunk.
 * When nothing remains, keeps the first sentence, or the first 80 characters.
 */
public final class ExcerptQuoteGuard {

    private static final int FALLBACK_CHARS = 80;
    private static final String TERMINATORS = "。！？.!?";

    private ExcerptQuoteGuard() {
    }

    public static List<String> sanitize(String chunk, List<String> raw) {
        String source = chunk == null ? "" : chunk;
        List<String> kept = new ArrayList<String>();
        if (raw != null) {
            for (int i = 0; i < raw.size(); i++) {
                String quote = raw.get(i);
                if (quote == null || quote.trim().isEmpty()) {
                    continue;
                }
                if (source.contains(quote)) {
                    kept.add(quote);
                }
            }
        }
        if (!kept.isEmpty()) {
            return kept;
        }
        String fallback = firstSentence(source);
        if (fallback.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(fallback);
    }

    private static String firstSentence(String chunk) {
        String text = chunk == null ? "" : chunk.trim();
        if (text.isEmpty()) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            if (TERMINATORS.indexOf(text.charAt(i)) >= 0) {
                return text.substring(0, i + 1);
            }
        }
        int end = Math.min(FALLBACK_CHARS, text.length());
        return text.substring(0, end);
    }
}

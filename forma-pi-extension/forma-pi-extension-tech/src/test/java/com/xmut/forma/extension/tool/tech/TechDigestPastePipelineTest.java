package com.xmut.forma.extension.tool.tech;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paste path: source prep ({@link TechDigestSourcePrep#slice}) then excerpt quote guard
 * ({@link ExcerptQuoteHelper#sanitize}). Simulates model quotes equal to each chunk's first sentence.
 * Paste success is the T4 open gate: credits settle only after a usable {@code view.json} is persisted
 * (粘贴成功才结算), not on fetch or excerpt generation alone.
 */
class TechDigestPastePipelineTest {

    private static final String NOT_IN_CHUNK = "\u0000__paste_pipeline_non_substring__";

    @Test
    void aiFixtureSliceThenSanitizeKeepsNonemptySubstringQuotes() throws Exception {
        String md = readFixture("techdigest/fixtures/ai.md");
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertFalse(result.getChunks().isEmpty(), "ai.md should produce at least one chunk");
        for (TechDigestChunk chunk : result.getChunks()) {
            String text = chunk.getText();
            String firstSentence = firstSentenceQuote(text);
            List<String> quotes = ExcerptQuoteHelper.sanitize(
                    text, Collections.singletonList(firstSentence));
            assertFalse(quotes.isEmpty(), "sanitize must yield quotes for chunk: " + chunk.getHeading());
            for (String quote : quotes) {
                assertFalse(quote.trim().isEmpty());
                assertTrue(text.contains(quote), "quote must be a substring of chunk text");
            }
        }
    }

    /** Same first-sentence rule as {@link ExcerptQuoteHelper} fallback (via non-matching raw). */
    private static String firstSentenceQuote(String chunk) {
        List<String> fallback = ExcerptQuoteHelper.sanitize(chunk, Collections.singletonList(NOT_IN_CHUNK));
        assertFalse(fallback.isEmpty(), "non-blank chunk should have a first-sentence fallback");
        return fallback.get(0);
    }

    private static String readFixture(String path) throws Exception {
        InputStream in = TechDigestPastePipelineTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        try (InputStream stream = in; Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

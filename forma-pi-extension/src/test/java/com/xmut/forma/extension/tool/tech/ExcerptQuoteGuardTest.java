package com.xmut.forma.extension.tool.tech;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcerptQuoteGuardTest {

    @Test
    void keepsSubstringQuotesAndDropsRewritesAndBlanks() {
        String chunk = "模型把句子改写了。这不是原文尾巴。";
        List<String> kept = ExcerptQuoteGuard.sanitize(chunk, Arrays.asList(
                "模型把句子改写了。",
                "  ",
                null,
                "",
                "完全改写的另一句话"));
        assertEquals(Collections.singletonList("模型把句子改写了。"), kept);
    }

    @Test
    void emptyKeptQuotesFallBackToFirstSentence() {
        String chunk = "你好世界。后面不要。";
        assertEquals(Collections.singletonList("你好世界。"),
                ExcerptQuoteGuard.sanitize(chunk, Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("你好世界。"),
                ExcerptQuoteGuard.sanitize(chunk, null));
        assertEquals(Collections.singletonList("你好世界。"),
                ExcerptQuoteGuard.sanitize(chunk, Collections.singletonList("改写了")));
    }

    @Test
    void terminatorsIncludeFullAndHalfWidthMarks() {
        assertEquals(Collections.singletonList("停！"),
                ExcerptQuoteGuard.sanitize("停！继续", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("问？"),
                ExcerptQuoteGuard.sanitize("问？继续", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("Wait!"),
                ExcerptQuoteGuard.sanitize("Wait! More.", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("Ask?"),
                ExcerptQuoteGuard.sanitize("Ask? More.", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("End."),
                ExcerptQuoteGuard.sanitize("End. Next.", Collections.<String>emptyList()));
    }

    @Test
    void noTerminatorFallsBackToFirstEightyChars() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            body.append('a');
        }
        String chunk = body.toString();
        List<String> kept = ExcerptQuoteGuard.sanitize(chunk, Collections.singletonList("rewritten"));
        assertEquals(1, kept.size());
        assertEquals(80, kept.get(0).length());
        assertEquals(chunk.substring(0, 80), kept.get(0));
        assertTrue(chunk.contains(kept.get(0)));
    }

    @Test
    void blankChunkReturnsEmpty() {
        assertTrue(ExcerptQuoteGuard.sanitize("   ", Collections.singletonList("x")).isEmpty());
        assertTrue(ExcerptQuoteGuard.sanitize(null, Collections.singletonList("x")).isEmpty());
    }
}

package com.xmut.forma.extension.tool.common.excerpt;

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
        List<String> kept = ExcerptQuoteHelper.sanitize(chunk, Arrays.asList(
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
                ExcerptQuoteHelper.sanitize(chunk, Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("你好世界。"),
                ExcerptQuoteHelper.sanitize(chunk, null));
        assertEquals(Collections.singletonList("你好世界。"),
                ExcerptQuoteHelper.sanitize(chunk, Collections.singletonList("改写了")));
    }

    @Test
    void terminatorsIncludeFullAndHalfWidthMarks() {
        assertEquals(Collections.singletonList("停！"),
                ExcerptQuoteHelper.sanitize("停！继续", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("问？"),
                ExcerptQuoteHelper.sanitize("问？继续", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("Wait!"),
                ExcerptQuoteHelper.sanitize("Wait! More.", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("Ask?"),
                ExcerptQuoteHelper.sanitize("Ask? More.", Collections.<String>emptyList()));
        assertEquals(Collections.singletonList("End."),
                ExcerptQuoteHelper.sanitize("End. Next.", Collections.<String>emptyList()));
    }

    @Test
    void noTerminatorFallsBackToFirstEightyChars() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            body.append('a');
        }
        String chunk = body.toString();
        List<String> kept = ExcerptQuoteHelper.sanitize(chunk, Collections.singletonList("rewritten"));
        assertEquals(1, kept.size());
        assertEquals(80, kept.get(0).length());
        assertEquals(chunk.substring(0, 80), kept.get(0));
        assertTrue(chunk.contains(kept.get(0)));
    }

    @Test
    void blankChunkReturnsEmpty() {
        assertTrue(ExcerptQuoteHelper.sanitize("   ", Collections.singletonList("x")).isEmpty());
        assertTrue(ExcerptQuoteHelper.sanitize(null, Collections.singletonList("x")).isEmpty());
    }
}

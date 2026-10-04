package com.xmut.forma.extension.tool.tech;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechDigestSourcePrepTest {

    private static final int MIN_CHUNK = 800;
    private static final int MAX_CHUNK = 1500;

    @Test
    void emptyInputYieldsNoChunks() {
        TechDigestPrepResult result = TechDigestSourcePrep.slice(null);
        assertTrue(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
        result = TechDigestSourcePrep.slice("");
        assertTrue(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
        result = TechDigestSourcePrep.slice("   \n  ");
        assertTrue(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
    }

    @Test
    void productFixtureProducesAtLeastOneChunk() throws Exception {
        String md = readFixture("techdigest/fixtures/product.md");
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertFalse(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
        TechDigestChunk first = result.getChunks().get(0);
        assertTrue(first.getText().length() > 0);
    }

    @Test
    void thousandCharBodyIsOneChunkWithoutOverlapTail() {
        StringBuilder body = new StringBuilder();
        while (body.length() < 1000) {
            body.append("甲乙丙丁戊己庚辛壬癸");
        }
        String text = body.substring(0, 1000);
        TechDigestPrepResult result = TechDigestSourcePrep.slice(text);
        assertEquals(1, result.getChunks().size());
        assertEquals(1000, result.getChunks().get(0).getText().length());
        assertFalse(result.isPartialCoverage());
    }

    @Test
    void shortStringProducesAtLeastOneChunk() {
        TechDigestPrepResult result = TechDigestSourcePrep.slice("简短说明：这是一段用于速读的测试文字。");
        assertFalse(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
    }

    @Test
    void bodyPastTwelveChunksMarksPartialCoverage() {
        StringBuilder body = new StringBuilder();
        while (body.length() < 20000) {
            body.append("甲乙丙丁戊己庚辛壬癸");
        }
        TechDigestPrepResult result = TechDigestSourcePrep.slice(body.substring(0, 20000));
        assertEquals(12, result.getChunks().size());
        assertTrue(result.isPartialCoverage());
    }

    @Test
    void longRfcFixtureFitsInTwelveChunks() throws Exception {
        String md = readFixture("techdigest/fixtures/long-rfc.md");
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertTrue(result.getChunks().size() <= 12);
        assertFalse(result.isPartialCoverage());
        for (TechDigestChunk chunk : result.getChunks()) {
            assertTrue(chunk.getText().length() <= MAX_CHUNK
                            || chunkContainsOnlyOversizedFence(chunk.getText()),
                    "chunk length " + chunk.getText().length());
            assertCompleteFencePairs(chunk.getText());
        }
    }

    @Test
    void fencedCodeBlockStaysIntactInOneChunk() {
        String md = "Intro paragraph before code.\n\n"
                + "```java\n"
                + "public class Demo {\n"
                + "  void run() { }\n"
                + "}\n"
                + "```\n\n"
                + "Outro after code.";
        String fenceBody = "```java\npublic class Demo {\n  void run() { }\n}\n```";
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertFalse(result.getChunks().isEmpty());
        boolean intact = false;
        for (TechDigestChunk chunk : result.getChunks()) {
            if (chunk.getText().contains(fenceBody)) {
                intact = true;
                break;
            }
        }
        assertTrue(intact, "code fence must appear whole in some chunk");
    }

    @Test
    void oversizedFenceBlockIsOneChunkWithIntactFences() {
        StringBuilder inner = new StringBuilder();
        while (inner.length() < 1600) {
            inner.append("// padding line for fence body\n");
        }
        String md = "```text\n" + inner + "```";
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertFalse(result.getChunks().isEmpty());
        List<TechDigestChunk> chunks = result.getChunks();
        assertTrue(chunks.size() >= 1);
        TechDigestChunk sole = chunks.get(0);
        assertTrue(sole.getText().contains("```text"));
        assertTrue(sole.getText().trim().endsWith("```"));
        assertTrue(sole.getText().length() > MAX_CHUNK);
        for (TechDigestChunk chunk : chunks) {
            assertCompleteFencePairs(chunk.getText());
        }
    }

    @Test
    void packedChunksRespectTargetSizeWhenSourceIsLong() throws Exception {
        String md = readFixture("techdigest/fixtures/long-rfc.md");
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        int largeEnough = 0;
        for (TechDigestChunk chunk : result.getChunks()) {
            if (chunk.getText().length() >= MIN_CHUNK) {
                largeEnough++;
            }
        }
        assertTrue(largeEnough >= 3, "expect several full-sized chunks from long fixture");
    }

    private static void assertCompleteFencePairs(String text) {
        if (!text.contains("```")) {
            return;
        }
        int pos = 0;
        while (pos < text.length()) {
            int open = indexOfLineStartFence(text, pos);
            if (open < 0) {
                break;
            }
            int close = indexOfLineStartFence(text, open + 3);
            assertTrue(close >= 0, "chunk tears a fenced region");
            int end = close;
            while (end < text.length() && text.charAt(end) != '\n') {
                end++;
            }
            if (end < text.length()) {
                end++;
            }
            pos = end;
        }
    }

    private static boolean chunkContainsOnlyOversizedFence(String text) {
        if (!text.contains("```")) {
            return false;
        }
        int open = indexOfLineStartFence(text, 0);
        if (open != 0) {
            return false;
        }
        int close = indexOfLineStartFence(text, open + 3);
        if (close < 0) {
            return false;
        }
        int end = close;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        if (end < text.length()) {
            end++;
        }
        return end == text.length() && text.length() > MAX_CHUNK;
    }

    private static int indexOfLineStartFence(String text, int from) {
        int i = text.indexOf("```", from);
        while (i >= 0) {
            if (i == 0 || text.charAt(i - 1) == '\n') {
                return i;
            }
            i = text.indexOf("```", i + 3);
        }
        return -1;
    }

    private static String readFixture(String path) throws Exception {
        InputStream in = TechDigestSourcePrepTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + path);
        }
        try (InputStream stream = in; Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

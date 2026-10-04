package com.xmut.forma.extension.tool.tech;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechDigestSourcePrepTest {

    private static final int MIN_CHUNK = 800;
    private static final int MAX_CHUNK = 1500;

    @Test
    void emptyInputYieldsNoChunks() {
        TechDigestPrepResult result = TechDigestSourcePrep.slice("");
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
    void shortStringProducesAtLeastOneChunk() {
        TechDigestPrepResult result = TechDigestSourcePrep.slice("简短说明：这是一段用于速读的测试文字。");
        assertFalse(result.getChunks().isEmpty());
        assertFalse(result.isPartialCoverage());
    }

    @Test
    void longRfcFixtureCapsAtTwelveChunksWithPartialCoverage() throws Exception {
        String md = readFixture("techdigest/fixtures/long-rfc.md");
        TechDigestPrepResult result = TechDigestSourcePrep.slice(md);
        assertTrue(result.getChunks().size() <= 12);
        assertTrue(result.isPartialCoverage());
        for (TechDigestChunk chunk : result.getChunks()) {
            assertTrue(chunk.getText().length() <= MAX_CHUNK,
                    "chunk length " + chunk.getText().length());
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

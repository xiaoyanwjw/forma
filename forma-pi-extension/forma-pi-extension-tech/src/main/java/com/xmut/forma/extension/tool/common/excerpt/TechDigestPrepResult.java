package com.xmut.forma.extension.tool.common.excerpt;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of {@link TechDigestSourcePrep#slice(String)}.
 */
public final class TechDigestPrepResult {

    private final List<TechDigestChunk> chunks;
    private final boolean partialCoverage;

    public TechDigestPrepResult(List<TechDigestChunk> chunks, boolean partialCoverage) {
        this.chunks = chunks == null
                ? Collections.<TechDigestChunk>emptyList()
                : Collections.unmodifiableList(chunks);
        this.partialCoverage = partialCoverage;
    }

    public List<TechDigestChunk> getChunks() {
        return chunks;
    }

    public boolean isPartialCoverage() {
        return partialCoverage;
    }
}

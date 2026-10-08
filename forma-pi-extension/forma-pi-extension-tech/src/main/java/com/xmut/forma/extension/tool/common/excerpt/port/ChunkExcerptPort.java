package com.xmut.forma.extension.tool.common.excerpt.port;

import com.xmut.forma.extension.tool.common.excerpt.ChunkExcerpt;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestChunk;
import java.util.List;

/**
 * 摘句 atom: source chunks → heading + quotes (raw until quote guard).
 * Not Agent-visible; consumed by business ingest tools.
 */
public interface ChunkExcerptPort {

    List<ChunkExcerpt> excerpt(List<TechDigestChunk> chunks);
}

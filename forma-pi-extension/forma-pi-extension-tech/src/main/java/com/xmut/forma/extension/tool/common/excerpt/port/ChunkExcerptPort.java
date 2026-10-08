package com.xmut.forma.extension.tool.common.excerpt.port;

import com.xmut.forma.extension.tool.tech.ChunkExcerpt;
import com.xmut.forma.extension.tool.tech.TechDigestChunk;
import java.util.List;

/**
 * 摘句 atom: source chunks → heading + quotes (raw until quote guard).
 * Agent still sees {@code excerpt_chunks} until later tasks.
 */
public interface ChunkExcerptPort {

    List<ChunkExcerpt> excerpt(List<TechDigestChunk> chunks);
}

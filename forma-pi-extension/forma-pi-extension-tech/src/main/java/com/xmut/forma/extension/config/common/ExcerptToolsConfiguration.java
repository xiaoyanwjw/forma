package com.xmut.forma.extension.config.common;

import com.xmut.forma.extension.tool.common.excerpt.ChunkExcerpt;
import com.xmut.forma.extension.tool.common.excerpt.ModelChunkExcerpter;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestChunk;
import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.pi.ai.model.ModelProvider;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes 摘句 {@link ChunkExcerptPort} for business tools only (not Agent-visible).
 * Without a {@link ModelProvider}, the Port returns empty excerpts (callers apply quote guards).
 */
@Configuration
public class ExcerptToolsConfiguration {

    @Bean
    public ChunkExcerptPort chunkExcerptPort(ObjectProvider<ModelProvider> models) {
        ModelProvider modelProvider = models.getIfAvailable();
        if (modelProvider == null) {
            return new ChunkExcerptPort() {
                @Override
                public List<ChunkExcerpt> excerpt(List<TechDigestChunk> chunks) {
                    return Collections.emptyList();
                }
            };
        }
        return new ModelChunkExcerpter(modelProvider);
    }
}

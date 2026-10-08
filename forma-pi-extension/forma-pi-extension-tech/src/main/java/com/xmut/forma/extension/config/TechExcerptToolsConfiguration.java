package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.extension.tool.tech.ChunkExcerpt;
import com.xmut.forma.extension.tool.tech.ExcerptChunksToolHandler;
import com.xmut.forma.extension.tool.tech.ModelChunkExcerpter;
import com.xmut.forma.extension.tool.tech.TechDigestChunk;
import com.xmut.forma.extension.tool.view.TechBriefingViewEnricher;
import com.xmut.forma.extension.tool.view.TechCompetitorViewEnricher;
import com.xmut.forma.extension.tool.view.TechDigestViewEnricher;
import com.xmut.forma.extension.tool.view.ViewEnricher;
import com.xmut.forma.pi.ai.model.ModelProvider;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code excerpt_chunks} and 摘句 {@link ChunkExcerptPort}.
 * Schema comes from {@code tools/tech/excerpt_chunks.tool.json}.
 * Without a {@link ModelProvider}, quotes fall back to the first sentence of each chunk.
 */
@Configuration
public class TechExcerptToolsConfiguration {

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

    @Bean
    public ExcerptChunksToolHandler excerptChunksToolHandler(ChunkExcerptPort chunkExcerptPort) {
        return new ExcerptChunksToolHandler(chunkExcerptPort);
    }

    @Bean
    public ViewEnricher techDigestViewEnricher() {
        return new TechDigestViewEnricher();
    }

    @Bean
    public ViewEnricher techCompetitorViewEnricher() {
        return new TechCompetitorViewEnricher();
    }

    @Bean
    public ViewEnricher techBriefingViewEnricher() {
        return new TechBriefingViewEnricher();
    }
}

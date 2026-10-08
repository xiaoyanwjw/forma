package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.tech.ExcerptChunksToolHandler;
import com.xmut.forma.extension.tool.tech.ModelChunkExcerpter;
import com.xmut.forma.extension.tool.view.TechBriefingViewEnricher;
import com.xmut.forma.extension.tool.view.TechCompetitorViewEnricher;
import com.xmut.forma.extension.tool.view.TechDigestViewEnricher;
import com.xmut.forma.extension.tool.view.ViewEnricher;
import com.xmut.forma.pi.ai.model.ModelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code excerpt_chunks}. Schema comes from {@code tools/tech/excerpt_chunks.tool.json}.
 * Without a {@link ModelProvider}, quotes fall back to the first sentence of each chunk.
 */
@Configuration
public class TechExcerptToolsConfiguration {

    @Bean
    public ExcerptChunksToolHandler excerptChunksToolHandler(ObjectProvider<ModelProvider> models) {
        ModelProvider modelProvider = models.getIfAvailable();
        ModelChunkExcerpter excerpter = modelProvider == null ? null : new ModelChunkExcerpter(modelProvider);
        return new ExcerptChunksToolHandler(excerpter);
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

package com.xmut.forma.extension.config;

import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import com.xmut.forma.extension.tool.digest.ingest.IngestDigestToolHandler;
import com.xmut.forma.extension.tool.product.ingest.IngestCompetitorToolHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code ingest_competitor} and {@code ingest_digest} business tools.
 * Schemas: {@code tools/product/ingest_competitor.tool.json},
 * {@code tools/digest/ingest_digest.tool.json}.
 */
@Configuration
public class PageIngestToolsConfiguration {

    @Bean
    public PageIngestService pageIngestService(PageFetchPort pageFetchPort, ChunkExcerptPort chunkExcerptPort) {
        return new PageIngestService(pageFetchPort, chunkExcerptPort);
    }

    @Bean
    public IngestCompetitorToolHandler ingestCompetitorToolHandler(PageIngestService pageIngestService) {
        return new IngestCompetitorToolHandler(pageIngestService);
    }

    @Bean
    public IngestDigestToolHandler ingestDigestToolHandler(PageIngestService pageIngestService) {
        return new IngestDigestToolHandler(pageIngestService);
    }
}

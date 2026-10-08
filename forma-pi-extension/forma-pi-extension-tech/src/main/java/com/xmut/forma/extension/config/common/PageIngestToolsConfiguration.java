package com.xmut.forma.extension.config.common;

import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.common.web.fetch.port.PageFetchPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers shared {@link PageIngestService} (抓取|paste → 摘句) for product/digest ingest tools.
 */
@Configuration
public class PageIngestToolsConfiguration {

    @Bean
    public PageIngestService pageIngestService(PageFetchPort pageFetchPort, ChunkExcerptPort chunkExcerptPort) {
        return new PageIngestService(pageFetchPort, chunkExcerptPort);
    }
}

package com.xmut.forma.extension.config.digest;

import com.xmut.forma.extension.tool.common.ingest.PageIngestService;
import com.xmut.forma.extension.tool.digest.ingest.IngestDigestToolHandler;
import com.xmut.forma.extension.tool.digest.view.TechDigestViewEnricher;
import com.xmut.forma.extension.tool.view.ViewEnricher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers digest-domain business tools and ViewEnrichers: {@code ingest_digest}.
 * Schema: {@code tools/digest/ingest_digest.tool.json}.
 */
@Configuration
public class DigestToolsConfiguration {

    @Bean
    public IngestDigestToolHandler ingestDigestToolHandler(PageIngestService pageIngestService) {
        return new IngestDigestToolHandler(pageIngestService);
    }

    @Bean
    public ViewEnricher techDigestViewEnricher() {
        return new TechDigestViewEnricher();
    }
}

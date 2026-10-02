package com.xmut.ebus.application.config;

import com.xmut.ebus.extension.tool.sku.SkuSearchProperties;
import com.xmut.ebus.application.business.agent.tool.xhs.XhsNoteSearchProperties;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.OverlayModelCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * Adam {@link ModelCatalog} overlay for SKU / XHS rerank use cases.
 *
 * <p>Does not declare {@link com.xmut.lims.pi.agent.tool.ToolCatalog}; the unique catalog
 * comes from pi-agent {@code AgentConfiguration}.
 */
@Configuration
@EnableConfigurationProperties({SkuSearchProperties.class, XhsNoteSearchProperties.class})
public class PiToolCatalogConfiguration {

    /**
     * Live {@link ModelCatalog} used by pi-ai routing. pi-agent / pi-ai only register
     * {@code pi.default}; SKU rerank needs {@code ebus.sku.rerank} on the same catalog.
     */
    @Primary
    @Bean
    public ModelCatalog modelCatalog(SkuSearchProperties skuProps, XhsNoteSearchProperties xhsProps) {
        return overlayRerankUseCases(skuProps, xhsProps);
    }

    static ModelCatalog overlayWithSkuRerank(SkuSearchProperties props) {
        return overlayRerankUseCases(props, new XhsNoteSearchProperties());
    }

    static ModelCatalog overlayRerankUseCases(SkuSearchProperties skuProps, XhsNoteSearchProperties xhsProps) {
        OverlayModelCatalog overlay = new OverlayModelCatalog(InMemoryModelCatalog.defaults());
        overlay.putOverride(
                resolveUseCase(skuProps == null ? null : skuProps.getSearcher().getRerankUseCase(), "ebus.sku.rerank"),
                rerankDescriptor(resolveUseCase(
                        skuProps == null ? null : skuProps.getSearcher().getRerankUseCase(), "ebus.sku.rerank")));
        overlay.putOverride(
                resolveUseCase(xhsProps == null ? null : xhsProps.getSearcher().getRerankUseCase(), "ebus.xhs.rerank"),
                rerankDescriptor(resolveUseCase(
                        xhsProps == null ? null : xhsProps.getSearcher().getRerankUseCase(), "ebus.xhs.rerank")));
        return overlay;
    }

    private static String resolveUseCase(String configured, String fallback) {
        return StringUtils.hasText(configured) ? configured : fallback;
    }

    static ModelDescriptor rerankDescriptor(String useCase) {
        return InMemoryModelCatalog.defaultChatDescriptor().toBuilder()
                .useCase(useCase)
                .temperature(0.0)
                .maxTokens(512)
                .build();
    }
}

package com.xmut.lims.pi.ai.provider;

import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.TokenConsumer;
import com.xmut.lims.pi.ai.model.UnsupportedModelException;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 按 Catalog {@code provider} 选择厂商 {@link ModelProvider}。本类不发 HTTP。
 */
public final class VendorRoutingModelProvider implements ModelProvider {

    private final Map<String, ModelProvider> byProvider;
    private final ModelCatalog catalog;

    public VendorRoutingModelProvider(Map<String, ModelProvider> byProvider, ModelCatalog catalog) {
        this.byProvider = byProvider != null
                ? Collections.unmodifiableMap(new HashMap<String, ModelProvider>(byProvider))
                : Collections.<String, ModelProvider>emptyMap();
        this.catalog = catalog;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        String useCase = StringUtils.hasText(request.getUseCase())
                ? request.getUseCase()
                : InMemoryModelCatalog.DEFAULT_USE_CASE;
        ModelDescriptor desc = catalog != null ? catalog.resolve(useCase) : null;
        String provider = desc != null ? desc.getProvider() : null;
        ModelProvider dest = provider != null ? byProvider.get(provider) : null;
        if (dest == null) {
            throw new UnsupportedModelException(useCase);
        }
        return dest.complete(request);
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }

        String useCase = StringUtils.hasText(request.getUseCase())
                ? request.getUseCase()
                : InMemoryModelCatalog.DEFAULT_USE_CASE;
        ModelDescriptor desc = catalog != null ? catalog.resolve(useCase) : null;
        String provider = desc != null ? desc.getProvider() : null;
        ModelProvider model = provider != null ? byProvider.get(provider) : null;
        if (model == null) {
            throw new UnsupportedModelException(useCase);
        }

        model.stream(request, consumer);
    }
}

package com.xmut.forma.pi.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 可运行时覆盖的 {@link ModelCatalog}：override 优先，否则回落 delegate。
 */
public final class OverlayModelCatalog implements ModelCatalog {

    private final ModelCatalog delegate;
    private final Map<String, ModelDescriptor> overrides = new LinkedHashMap<>();

    public OverlayModelCatalog(ModelCatalog delegate) {
        this.delegate = delegate;
    }

    public void putOverride(String useCase, ModelDescriptor descriptor) {
        overrides.put(useCase, descriptor);
    }

    @Override
    public ModelDescriptor resolve(String useCase) {
        ModelDescriptor override = overrides.get(useCase);
        if (override != null) {
            return override;
        }
        return delegate.resolve(useCase);
    }

    @Override
    public Set<String> registeredUseCases() {
        Set<String> merged = new LinkedHashSet<>(delegate.registeredUseCases());
        merged.addAll(overrides.keySet());
        return Collections.unmodifiableSet(merged);
    }
}

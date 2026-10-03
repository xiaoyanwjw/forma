package com.xmut.forma.pi.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内存 Catalog：Adam 近端仅注册默认对话用例。
 */
public final class InMemoryModelCatalog implements ModelCatalog {

    public static final String DEFAULT_USE_CASE = "pi.default";

    private final Map<String, ModelDescriptor> byUseCase;

    public InMemoryModelCatalog(Map<String, ModelDescriptor> descriptors) {
        Map<String, ModelDescriptor> copy = new LinkedHashMap<>();
        if (descriptors != null) {
            copy.putAll(descriptors);
        }
        this.byUseCase = Collections.unmodifiableMap(copy);
    }

    /** 默认：DeepSeek chat（有 Key 走真模型；无 Key 时 PiAi 装配 Stub，不读 provider）。 */
    public static InMemoryModelCatalog defaults() {
        return new InMemoryModelCatalog(Collections.singletonMap(DEFAULT_USE_CASE, defaultChatDescriptor()));
    }

    public static ModelDescriptor defaultChatDescriptor() {
        return ModelDescriptor.builder()
                .useCase(DEFAULT_USE_CASE)
                .provider("deepseek")
                .model("deepseek-v4-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .supportsNativeToolCalling(true)
                .dailyQuotaPerTenant(0)
                .temperature(0.3)
                .maxTokens(16384)
                .thinkingMode("disabled")
                .maxAttempts(1)
                .cacheTtlSeconds(0)
                .build();
    }

    @Override
    public ModelDescriptor resolve(String useCase) {
        String key = useCase != null ? useCase : DEFAULT_USE_CASE;
        ModelDescriptor desc = byUseCase.get(key);
        if (desc == null) {
            throw new UnsupportedModelException(key);
        }
        return desc;
    }

    @Override
    public Set<String> registeredUseCases() {
        return byUseCase.keySet();
    }
}

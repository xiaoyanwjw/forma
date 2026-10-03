package com.xmut.forma.pi.ai.model.decorator;

import com.xmut.forma.pi.ai.model.*;
import com.xmut.forma.pi.ai.model.port.ModelCache;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

/**
 * LLM 响应缓存装饰器 <b>[Lippi]</b>。
 *
 * <p><b>与业务缓存分离（NFR11）</b>：仅缓存 {@link ModelResponse}；
 * 业务域缓存不得复用本 Decorator 的 key / TTL。
 */
public final class CachedModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final ModelCache cache;
    private final ModelCatalog catalog;

    public CachedModelProvider(ModelProvider delegate, ModelCache cache, ModelCatalog catalog) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate required");
        }
        this.delegate = delegate;
        this.cache = cache != null ? cache : ModelCache.NOOP;
        this.catalog = catalog;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        int ttl = resolveTtl(request);
        if (ttl <= 0) {
            return delegate.complete(request);
        }
        // 带 tools 的调用默认不缓存（工具结果易变）
        if (request != null && request.getTools() != null && !request.getTools().isEmpty()) {
            return delegate.complete(request);
        }
        String key = ModelCache.simpleKey(request);
        Optional<ModelResponse> hit = cache.get(key);
        if (hit.isPresent()) {
            return ModelResponse.norm(hit.get());
        }
        ModelResponse response = ModelResponse.norm(delegate.complete(request));
        cache.put(key, response, ttl);
        return response;
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        int ttl = resolveTtl(request);
        final List<ToolSchema> tools = request.getTools();
        if (ttl <= 0 || !CollectionUtils.isEmpty(tools)) {
            delegate.stream(request, consumer);
            return;
        }

        String key = ModelCache.simpleKey(request);
        Optional<ModelResponse> hit = cache.get(key);
        if (hit.isPresent()) {
            ModelResponse cached = ModelResponse.norm(hit.get());
            if (cached.getContent() != null && !cached.getContent().isEmpty()) {
                consumer.onTextDelta(cached.getContent());
            }
            consumer.onComplete(cached);
            return;
        }
        delegate.stream(request, new TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                consumer.onTextDelta(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
                ModelResponse norm = ModelResponse.norm(response);
                cache.put(key, norm, ttl);
                consumer.onComplete(norm);
            }
        });
    }

    private int resolveTtl(ModelRequest request) {
        if (request == null || catalog == null || request.getUseCase() == null) {
            return 0;
        }
        try {
            ModelDescriptor desc = catalog.resolve(request.getUseCase());
            Integer ttl = desc != null ? desc.getCacheTtlSeconds() : null;
            return ttl != null ? ttl : 0;
        } catch (UnsupportedModelException e) {
            return 0;
        }
    }
}

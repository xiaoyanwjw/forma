package com.xmut.forma.pi.ai.model;

import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 按 Catalog {@code supportsNativeToolCalling} 路由 native / 文本回落 <b>[Lippi]</b>。
 *
 * <p>委托底层 Provider 发实际补全；本类不发 HTTP。
 */
public final class ProtocolRoutingModelProvider implements ModelProvider {

    private final ModelProvider delegate;
    private final ModelCatalog catalog;
    private final TextToolCallParser textParser;
    private final NativeToolCallMapper nativeMapper;

    public ProtocolRoutingModelProvider(ModelProvider delegate, ModelCatalog catalog) {
        this(delegate, catalog, new TextToolCallParser(), new NativeToolCallMapper());
    }

    public ProtocolRoutingModelProvider(ModelProvider delegate,
                                        ModelCatalog catalog,
                                        TextToolCallParser textParser,
                                        NativeToolCallMapper nativeMapper) {
        this.delegate = delegate;
        this.catalog = catalog;
        this.textParser = textParser != null ? textParser : new TextToolCallParser();
        this.nativeMapper = nativeMapper != null ? nativeMapper : new NativeToolCallMapper();
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("request required");
        }
        if (useFallback(request)) {
            return fallback(request);
        }
        return nativeNormalize(delegate.complete(request));
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("request required");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer required");
        }
//        if (useFallback(request)) {
//            ModelResponse r = ModelResponse.norm(fallback(request));
//            if (r.getContent() != null && !r.getContent().isEmpty()) {
//                consumer.onTextDelta(r.getContent());
//            }
//            consumer.onComplete(r);
//            return;
//        }
        delegate.stream(request, new TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                consumer.onTextDelta(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
                consumer.onComplete(nativeNormalize(response));
            }
        });
    }

    private boolean useFallback(ModelRequest request) {
        if (CollectionUtils.isEmpty(request.getTools())) {
            return false;
        }
        ModelDescriptor desc = resolveDescriptor(request.getUseCase());
        return desc != null && !desc.isSupportsNativeToolCalling();
    }

    private ModelResponse fallback(ModelRequest request) {
        List<ToolSchema> tools = request.getTools();
        ModelRequest textRequest = request.toBuilder()
                .tools(null)
                .messages(TextToolCallParser.withToolPrompt(
                        request.getMessages() != null ? request.getMessages() : Collections.emptyList(),
                        tools))
                .build();
        ModelResponse raw = ModelResponse.norm(delegate.complete(textRequest));
        String content = raw.getContent();
        List<ToolCallEntry> parsed = textParser.parse(content);
        if (!CollectionUtils.isEmpty(parsed)) {
            content = TextToolCallParser.stripToolScaffolding(content);
        }
        return ModelResponse.builder()
                .content(content)
                .toolCalls(parsed)
                .finishReason(parsed.isEmpty()
                        ? (raw.getFinishReason() != null ? raw.getFinishReason() : "stop")
                        : "tool_calls")
                .modelVersion(raw.getModelVersion())
                .promptTokens(raw.getPromptTokens())
                .completionTokens(raw.getCompletionTokens())
                .totalTokens(raw.getTotalTokens())
                .build();
    }

    private ModelResponse nativeNormalize(ModelResponse response) {
        ModelResponse raw = ModelResponse.norm(response);
        return ModelResponse.builder()
                .content(raw.getContent())
                .toolCalls(nativeMapper.normalize(raw.getToolCalls()))
                .finishReason(raw.getFinishReason())
                .modelVersion(raw.getModelVersion())
                .promptTokens(raw.getPromptTokens())
                .completionTokens(raw.getCompletionTokens())
                .totalTokens(raw.getTotalTokens())
                .build();
    }

    /**
     * Catalog 未配置时返回 null（走 native）；已配置但 useCase 未知则抛 {@link UnsupportedModelException}。
     */
    private ModelDescriptor resolveDescriptor(String useCase) {
        if (catalog == null) {
            return null;
        }

        return catalog.resolve(useCase != null ? useCase : InMemoryModelCatalog.DEFAULT_USE_CASE);
    }
}

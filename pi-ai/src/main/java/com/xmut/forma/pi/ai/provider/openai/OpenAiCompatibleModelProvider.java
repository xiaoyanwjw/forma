package com.xmut.forma.pi.ai.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.model.InMemoryModelCatalog;
import com.xmut.forma.pi.ai.model.ModelCatalog;
import com.xmut.forma.pi.ai.model.ModelDescriptor;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.model.TokenConsumer;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 单一厂商的 OpenAI 兼容 {@link ModelProvider}。模型名来自 Catalog。
 */
public final class OpenAiCompatibleModelProvider implements ModelProvider {

    private final OpenAiCompatibleHttpClient httpClient;
    private final OpenAiCompatiblePayloadBuilder payloadBuilder;
    private final OpenAiCompatibleResponseParser responseParser;
    private final ModelCatalog catalog;
    private final ObjectMapper mapper;

    public OpenAiCompatibleModelProvider(OpenAiCompatibleHttpClient httpClient,
                                         ObjectMapper mapper,
                                         ModelCatalog catalog) {
        this.httpClient = httpClient;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.payloadBuilder = new OpenAiCompatiblePayloadBuilder(this.mapper);
        this.responseParser = new OpenAiCompatibleResponseParser(this.mapper);
        this.catalog = catalog;
    }

    @Override
    public ModelResponse complete(ModelRequest request) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("request required");
        }

        ModelDescriptor desc = resolveDescriptor(request.getUseCase());
        String model = desc != null && StringUtils.hasText(desc.getModel()) ? desc.getModel() : "qwen-flash";
        String payload = payloadBuilder.build(request, desc, false);
        String body = httpClient.complete(payload, request.getTraceId(), model);
        return responseParser.parse(body, model);
    }

    @Override
    public void stream(ModelRequest request, TokenConsumer consumer) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("request required");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer required");
        }
        ModelDescriptor desc = resolveDescriptor(request.getUseCase());
        final String model = desc != null && StringUtils.hasText(desc.getModel()) ? desc.getModel() : "qwen-flash";
        String payload = payloadBuilder.build(request, desc, true);
        final OpenAiStreamAssembler assembler = new OpenAiStreamAssembler(mapper, model);
        httpClient.stream(payload, request.getTraceId(), model, new SseEventConsumer() {
            @Override
            public void onNext(String jsonObject) {
                try {
                    String delta = assembler.accept(mapper.readTree(jsonObject));
                    if (delta != null) {
                        consumer.onTextDelta(delta);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new com.xmut.forma.pi.ai.exception.PiModelIoException("stream chunk not JSON", e);
                }
            }

            @Override
            public void onDone() {
                consumer.onComplete(assembler.build());
            }
        });
    }

    private ModelDescriptor resolveDescriptor(String useCase) {
        if (catalog == null) {
            return null;
        }
        String key = StringUtils.hasText(useCase) ? useCase : InMemoryModelCatalog.DEFAULT_USE_CASE;
        return catalog.resolve(key);
    }
}

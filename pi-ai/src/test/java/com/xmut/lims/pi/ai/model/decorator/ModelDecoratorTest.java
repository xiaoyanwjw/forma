package com.xmut.lims.pi.ai.model.decorator;

import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.port.ModelCache;
import com.xmut.lims.pi.ai.model.port.ModelCallRecord;
import com.xmut.lims.pi.ai.model.port.ModelCallRecorder;
import com.xmut.lims.pi.ai.model.port.ModelRateLimit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelDecoratorTest {

    @Test
    void rate_limit_blocks_when_quota_exceeded() {
        ModelCatalog catalog = catalogWithQuota(1);
        ModelRateLimit limit = (sessionId, useCase, dailyLimit) -> false;
        ModelProvider inner = request -> ModelResponse.builder().content("x").build();
        ModelProvider sut = new RateLimitedModelProvider(inner, limit, catalog);

        assertThatThrownBy(() -> sut.complete(baseRequest("uc")))
                .isInstanceOf(ModelRateLimitExceededException.class)
                .hasMessageContaining("sessionId=s1");
    }

    @Test
    void rate_limit_acquire_uses_sessionId() {
        ModelCatalog catalog = catalogWithQuota(5);
        AtomicInteger seenLimit = new AtomicInteger();
        AtomicReference<String> seenSession = new AtomicReference<>();
        ModelRateLimit limit = (sessionId, useCase, dailyLimit) -> {
            seenSession.set(sessionId);
            seenLimit.set(dailyLimit);
            return true;
        };
        ModelProvider inner = request -> ModelResponse.builder().content("x").build();
        ModelProvider sut = new RateLimitedModelProvider(inner, limit, catalog);

        assertThat(sut.complete(baseRequest("uc")).getContent()).isEqualTo("x");
        assertThat(seenSession.get()).isEqualTo("s1");
        assertThat(seenLimit.get()).isEqualTo(5);
    }

    @Test
    void rate_limit_blank_session_uses_underscore_key() {
        ModelCatalog catalog = catalogWithQuota(1);
        AtomicReference<String> seenSession = new AtomicReference<>();
        ModelRateLimit limit = (sessionId, useCase, dailyLimit) -> {
            seenSession.set(sessionId);
            return true;
        };
        ModelProvider inner = request -> ModelResponse.builder().content("x").build();
        ModelProvider sut = new RateLimitedModelProvider(inner, limit, catalog);

        ModelRequest blank = ModelRequest.builder()
                .useCase("uc")
                .sessionId("  ")
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("hello").build()))
                .build();
        assertThat(sut.complete(blank).getContent()).isEqualTo("x");
        assertThat(seenSession.get()).isEqualTo("_");
    }

    @Test
    void audited_records_success() {
        List<ModelCallRecord> records = new ArrayList<>();
        ModelCallRecorder recorder = records::add;
        ModelProvider inner = request -> ModelResponse.builder().content("ok").build();
        ModelProvider sut = new AuditedModelProvider(inner, recorder);

        ModelResponse response = sut.complete(baseRequest("uc"));
        assertThat(response.getContent()).isEqualTo("ok");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(ModelCallRecord.STATUS_SUCCESS);
        assertThat(records.get(0).getSessionId()).isEqualTo("s1");
    }

    @Test
    void cached_hits_on_second_call() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider inner = request -> {
            calls.incrementAndGet();
            return ModelResponse.builder().content("cached-body").build();
        };
        ModelCatalog catalog = new InMemoryModelCatalog(Collections.singletonMap("uc",
                ModelDescriptor.builder()
                        .useCase("uc")
                        .provider("stub")
                        .model("m")
                        .cacheTtlSeconds(60)
                        .build()));
        ModelCache cache = new InMemoryModelCache();
        ModelProvider sut = new CachedModelProvider(inner, cache, catalog);

        ModelRequest req = baseRequest("uc");
        assertThat(sut.complete(req).getContent()).isEqualTo("cached-body");
        assertThat(sut.complete(req).getContent()).isEqualTo("cached-body");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retried_retries_transient_failure() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider inner = request -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException(new java.net.SocketTimeoutException("temp"));
            }
            return ModelResponse.builder().content("recovered").build();
        };
        ModelCatalog catalog = new InMemoryModelCatalog(Collections.singletonMap("uc",
                ModelDescriptor.builder()
                        .useCase("uc")
                        .provider("stub")
                        .model("m")
                        .maxAttempts(3)
                        .build()));
        ModelProvider sut = new RetriedModelProvider(inner, catalog);

        assertThat(sut.complete(baseRequest("uc")).getContent()).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void wrap_chain_order_outermost_is_rate_limited() {
        ModelCatalog catalog = catalogWithQuota(100);
        AtomicInteger calls = new AtomicInteger();
        ModelProvider inner = request -> {
            calls.incrementAndGet();
            return ModelResponse.builder().content("y").build();
        };
        ModelProvider wrapped = ModelDecorators.wrapWithNoopPorts(inner, catalog);
        assertThat(wrapped).isInstanceOf(RateLimitedModelProvider.class);
        assertThat(wrapped.complete(baseRequest("uc")).getContent()).isEqualTo("y");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void wrap_chain_stream_does_not_call_complete() {
        ModelCatalog catalog = catalogWithQuota(100);
        AtomicInteger completes = new AtomicInteger();
        AtomicInteger streams = new AtomicInteger();
        ModelProvider inner = new ModelProvider() {
            @Override
            public ModelResponse complete(ModelRequest request) {
                completes.incrementAndGet();
                return ModelResponse.builder().content("from-complete").build();
            }

            @Override
            public void stream(ModelRequest request, com.xmut.lims.pi.ai.model.TokenConsumer consumer) {
                streams.incrementAndGet();
                consumer.onTextDelta("z");
                consumer.onComplete(ModelResponse.builder().content("z").build());
            }
        };
        ModelProvider wrapped = ModelDecorators.wrapWithNoopPorts(inner, catalog);
        java.util.List<String> deltas = new ArrayList<>();
        wrapped.stream(baseRequest("uc"), new com.xmut.lims.pi.ai.model.TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
            }
        });
        assertThat(streams.get()).isEqualTo(1);
        assertThat(completes.get()).isEqualTo(0);
        assertThat(deltas).containsExactly("z");
    }

    @Test
    void catalog_switch_changes_native_flag() {
        ModelDescriptor nativeDesc = ModelDescriptor.builder()
                .useCase("a").provider("p").model("m").supportsNativeToolCalling(true).build();
        ModelDescriptor textDesc = ModelDescriptor.builder()
                .useCase("b").provider("p").model("m").supportsNativeToolCalling(false).build();
        java.util.Map<String, ModelDescriptor> map = new java.util.LinkedHashMap<>();
        map.put("a", nativeDesc);
        map.put("b", textDesc);
        ModelCatalog catalog = new InMemoryModelCatalog(map);

        assertThat(catalog.resolve("a").isSupportsNativeToolCalling()).isTrue();
        assertThat(catalog.resolve("b").isSupportsNativeToolCalling()).isFalse();
    }

    private static ModelRequest baseRequest(String useCase) {
        return ModelRequest.builder()
                .useCase(useCase)
                .sessionId("s1")
                .messages(Collections.singletonList(
                        Message.builder().role("user").content("hello").build()))
                .build();
    }

    private static ModelCatalog catalogWithQuota(int daily) {
        return new InMemoryModelCatalog(Collections.singletonMap("uc",
                ModelDescriptor.builder()
                        .useCase("uc")
                        .provider("stub")
                        .model("m")
                        .dailyQuotaPerTenant(daily)
                        .build()));
    }

    private static final class InMemoryModelCache implements ModelCache {
        private final java.util.Map<String, ModelResponse> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Optional<ModelResponse> get(String cacheKey) {
            return Optional.ofNullable(store.get(cacheKey));
        }

        @Override
        public void put(String cacheKey, ModelResponse response, int ttlSeconds) {
            store.put(cacheKey, response);
        }
    }
}

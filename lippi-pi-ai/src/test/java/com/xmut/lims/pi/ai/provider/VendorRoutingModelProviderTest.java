package com.xmut.lims.pi.ai.provider;

import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.ai.model.InMemoryModelCatalog;
import com.xmut.lims.pi.ai.model.ModelCatalog;
import com.xmut.lims.pi.ai.model.ModelDescriptor;
import com.xmut.lims.pi.ai.model.ModelModality;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import com.xmut.lims.pi.ai.model.UnsupportedModelException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VendorRoutingModelProviderTest {

    @Test
    void routes_by_catalog_provider() {
        Map<String, ModelDescriptor> descriptors = new LinkedHashMap<String, ModelDescriptor>();
        descriptors.put("pi.default", ModelDescriptor.builder()
                .useCase("pi.default")
                .provider("dashscope")
                .model("qwen-flash")
                .modalities(Collections.singleton(ModelModality.CHAT))
                .build());
        descriptors.put("other", ModelDescriptor.builder()
                .useCase("other")
                .provider("deepseek")
                .model("deepseek-chat")
                .build());
        ModelCatalog catalog = new InMemoryModelCatalog(descriptors);
        Map<String, ModelProvider> vendors = new HashMap<String, ModelProvider>();
        vendors.put("dashscope", req -> ModelResponse.builder().content("from-ds").build());
        vendors.put("deepseek", req -> ModelResponse.builder().content("from-dk").build());
        VendorRoutingModelProvider routed = new VendorRoutingModelProvider(vendors, catalog);

        assertThat(routed.complete(ModelRequest.builder()
                .useCase("pi.default")
                .messages(Collections.singletonList(Message.user("x")))
                .build()).getContent()).isEqualTo("from-ds");
        assertThat(routed.complete(ModelRequest.builder()
                .useCase("other")
                .messages(Collections.singletonList(Message.user("x")))
                .build()).getContent()).isEqualTo("from-dk");
    }

    @Test
    void unknown_provider_throws() {
        ModelCatalog catalog = InMemoryModelCatalog.defaults();
        VendorRoutingModelProvider routed = new VendorRoutingModelProvider(
                Collections.<String, ModelProvider>emptyMap(), catalog);
        assertThatThrownBy(() -> routed.complete(ModelRequest.builder()
                .useCase("pi.default")
                .messages(Collections.singletonList(Message.user("x")))
                .build()))
                .isInstanceOf(UnsupportedModelException.class);
    }

    @Test
    void stream_delegates_to_vendor_not_complete_fallback() {
        Map<String, ModelDescriptor> descriptors = new LinkedHashMap<String, ModelDescriptor>();
        descriptors.put("pi.default", ModelDescriptor.builder()
                .useCase("pi.default")
                .provider("dashscope")
                .model("qwen-flash")
                .build());
        ModelCatalog catalog = new InMemoryModelCatalog(descriptors);
        java.util.concurrent.atomic.AtomicInteger streams = new java.util.concurrent.atomic.AtomicInteger();
        ModelProvider vendor = new ModelProvider() {
            @Override
            public ModelResponse complete(ModelRequest request) {
                throw new AssertionError("stream must not fall back to complete");
            }

            @Override
            public void stream(ModelRequest request, com.xmut.lims.pi.ai.model.TokenConsumer consumer) {
                streams.incrementAndGet();
                consumer.onTextDelta("He");
                consumer.onTextDelta("llo");
                consumer.onComplete(ModelResponse.builder().content("Hello").build());
            }
        };
        Map<String, ModelProvider> vendors = new HashMap<String, ModelProvider>();
        vendors.put("dashscope", vendor);
        VendorRoutingModelProvider routed = new VendorRoutingModelProvider(vendors, catalog);

        java.util.List<String> deltas = new java.util.ArrayList<String>();
        routed.stream(ModelRequest.builder()
                .useCase("pi.default")
                .messages(Collections.singletonList(Message.user("x")))
                .build(), new com.xmut.lims.pi.ai.model.TokenConsumer() {
            @Override
            public void onTextDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelResponse response) {
            }
        });
        assertThat(streams.get()).isEqualTo(1);
        assertThat(deltas).containsExactly("He", "llo");
    }
}

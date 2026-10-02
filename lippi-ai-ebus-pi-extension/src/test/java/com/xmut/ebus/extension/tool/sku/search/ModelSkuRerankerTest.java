package com.xmut.ebus.extension.tool.sku.search;

import com.xmut.ebus.extension.tool.sku.port.SkuSearchProperties;
import com.xmut.lims.pi.ai.model.ModelProvider;
import com.xmut.lims.pi.ai.model.ModelRequest;
import com.xmut.lims.pi.ai.model.ModelResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelSkuRerankerTest {

    @Mock
    private ModelProvider model;

    private SkuSearchProperties props;
    private ModelSkuReranker reranker;

    @BeforeEach
    void setUp() {
        props = new SkuSearchProperties();
        reranker = new ModelSkuReranker(model, props);
    }

    @Test
    void orderIds_parsesIdsJson() {
        when(model.complete(any())).thenReturn(ModelResponse.builder().content("{\"ids\":[\"h2\",\"h1\"]}").build());
        List<String> ids = new ModelSkuReranker(model, props).orderIds("杯垫", Arrays.asList(c("h1"), c("h2")));
        assertEquals(Arrays.asList("h2", "h1"), ids);
        ArgumentCaptor<ModelRequest> cap = ArgumentCaptor.forClass(ModelRequest.class);
        verify(model).complete(cap.capture());
        assertEquals("ebus.sku.rerank", cap.getValue().getUseCase());
        assertFalse(cap.getValue().getMessages().toString().contains("https://"));
    }

    @Test
    void orderIds_garbage_returnsEmpty() {
        when(model.complete(any())).thenReturn(ModelResponse.builder().content("not-json").build());
        assertTrue(reranker.orderIds("q", Arrays.asList(c("h1"))).isEmpty());
    }

    @Test
    void orderIds_stripsFenceAndAcceptsBareArray() {
        when(model.complete(any())).thenReturn(
                ModelResponse.builder().content("```json\n[\"h2\",\"h1\"]\n```").build());
        assertEquals(Arrays.asList("h2", "h1"), reranker.orderIds("杯垫", Arrays.asList(c("h1"), c("h2"))));
    }

    @Test
    void orderIds_dropsUnknownIds() {
        when(model.complete(any())).thenReturn(
                ModelResponse.builder().content("{\"ids\":[\"h9\",\"h2\",\"h1\"]}").build());
        assertEquals(Arrays.asList("h2", "h1"), reranker.orderIds("q", Arrays.asList(c("h1"), c("h2"))));
    }

    private static SkuCandidate c(String id) {
        return new SkuCandidate(id, "taobao_tbk", "title-" + id, "1", "cat", "https://example.com/" + id, "ref");
    }
}

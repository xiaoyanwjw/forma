package com.xmut.ebus.extension.tool.xhs.search;

import com.xmut.ebus.extension.tool.xhs.port.XhsNoteSearchProperties;
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
class ModelXhsNoteRerankerTest {

    @Mock
    private ModelProvider model;

    private XhsNoteSearchProperties props;
    private ModelXhsNoteReranker reranker;

    @BeforeEach
    void setUp() {
        props = new XhsNoteSearchProperties();
        reranker = new ModelXhsNoteReranker(model, props);
    }

    @Test
    void orderIds_parsesIdsJson() {
        when(model.complete(any())).thenReturn(ModelResponse.builder().content("{\"ids\":[\"h2\",\"h1\"]}").build());
        List<String> ids = reranker.orderIds("杯垫", Arrays.asList(c("h1"), c("h2")));
        assertEquals(Arrays.asList("h2", "h1"), ids);
        ArgumentCaptor<ModelRequest> cap = ArgumentCaptor.forClass(ModelRequest.class);
        verify(model).complete(cap.capture());
        assertEquals("ebus.xhs.rerank", cap.getValue().getUseCase());
        assertFalse(cap.getValue().getMessages().toString().contains("https://"));
    }

    @Test
    void orderIds_garbage_returnsEmpty() {
        when(model.complete(any())).thenReturn(ModelResponse.builder().content("not-json").build());
        assertTrue(reranker.orderIds("q", Arrays.asList(c("h1"))).isEmpty());
    }

    private static XhsNoteCandidate c(String id) {
        return new XhsNoteCandidate(
                id,
                "note-" + id,
                "title-" + id,
                "desc",
                "https://www.xiaohongshu.com/explore/" + id,
                "1",
                "author");
    }
}

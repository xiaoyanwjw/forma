package com.xmut.forma.extension.tool.tech;

import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelChunkExcerpterTest {

    private static final String SYSTEM =
            "你只从本块原文复制值得留下的连续句子。只输出 JSON {\"quotes\":[\"...\"]}。不要改写、不要块外知识、不要解释。条数自定，可以是空数组。";

    private static final String ALPHA = "ALPHA_BODY_SENTENCE_ONE. ALPHA_BODY_SENTENCE_TWO.";
    private static final String BETA = "BETA_BODY_SENTENCE_ONE. BETA_BODY_SENTENCE_TWO.";

    @Mock
    private ModelProvider model;

    @Test
    void completeBatchOncePerChunkWithIsolatedUserText() {
        when(model.completeBatch(anyList())).thenReturn(Arrays.asList(
                ModelResponse.builder().content("{\"quotes\":[\"ALPHA_BODY_SENTENCE_ONE.\"]}").build(),
                ModelResponse.builder().content("{\"quotes\":[\"BETA_BODY_SENTENCE_ONE.\"]}").build()));

        List<ChunkExcerpt> excerpts = new ModelChunkExcerpter(model).excerpt(Arrays.asList(
                new TechDigestChunk("HEADING_ALPHA", ALPHA),
                new TechDigestChunk("HEADING_BETA", BETA)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(model, times(1)).completeBatch(captor.capture());
        verify(model, never()).complete(any());

        List<ModelRequest> requests = captor.getValue();
        assertEquals(2, requests.size());
        assertRequest(requests.get(0), "HEADING_ALPHA", ALPHA, BETA);
        assertRequest(requests.get(1), "HEADING_BETA", BETA, ALPHA);

        assertEquals(2, excerpts.size());
        assertEquals("HEADING_ALPHA", excerpts.get(0).getHeading());
        assertEquals(Collections.singletonList("ALPHA_BODY_SENTENCE_ONE."), excerpts.get(0).getQuotes());
        assertEquals("HEADING_BETA", excerpts.get(1).getHeading());
        assertEquals(Collections.singletonList("BETA_BODY_SENTENCE_ONE."), excerpts.get(1).getQuotes());
    }

    @Test
    void parseFailureYieldsEmptyQuotes() {
        when(model.completeBatch(anyList())).thenReturn(Collections.singletonList(
                ModelResponse.builder().content("not-json").build()));
        List<ChunkExcerpt> excerpts = new ModelChunkExcerpter(model).excerpt(Collections.singletonList(
                new TechDigestChunk("h", "正文。")));
        assertEquals(1, excerpts.size());
        assertTrue(excerpts.get(0).getQuotes().isEmpty());
    }

    @Test
    void fencedJsonParsesQuotes() {
        when(model.completeBatch(anyList())).thenReturn(Collections.singletonList(
                ModelResponse.builder().content("```json\n{\"quotes\":[\"ALPHA_BODY_SENTENCE_ONE.\"]}\n```").build()));
        List<ChunkExcerpt> excerpts = new ModelChunkExcerpter(model).excerpt(Collections.singletonList(
                new TechDigestChunk("HEADING_ALPHA", ALPHA)));
        assertEquals(Collections.singletonList("ALPHA_BODY_SENTENCE_ONE."), excerpts.get(0).getQuotes());
    }

    @Test
    void completeBatchFailureYieldsAlignedEmptyQuotes() {
        when(model.completeBatch(anyList())).thenThrow(new IllegalStateException("boom"));
        List<ChunkExcerpt> excerpts = new ModelChunkExcerpter(model).excerpt(Arrays.asList(
                new TechDigestChunk("HEADING_ALPHA", ALPHA),
                new TechDigestChunk("HEADING_BETA", BETA)));
        verify(model, times(1)).completeBatch(anyList());
        verify(model, never()).complete(any());
        assertEquals(2, excerpts.size());
        assertTrue(excerpts.get(0).getQuotes().isEmpty());
        assertTrue(excerpts.get(1).getQuotes().isEmpty());
        assertEquals("HEADING_ALPHA", excerpts.get(0).getHeading());
        assertEquals("HEADING_BETA", excerpts.get(1).getHeading());
    }

    private static void assertRequest(ModelRequest request, String heading, String own, String other) {
        assertEquals("forma.tech.excerpt", request.getUseCase());
        assertEquals(Double.valueOf(0.0), request.getTemperature());
        assertEquals(Integer.valueOf(1024), request.getMaxTokens());
        assertEquals(2, request.getMessages().size());
        Message system = request.getMessages().get(0);
        Message user = request.getMessages().get(1);
        assertEquals("system", system.getRole());
        assertEquals(SYSTEM, system.getContent());
        assertEquals("user", user.getRole());
        assertTrue(user.getContent().contains(heading));
        assertTrue(user.getContent().contains(own));
        assertFalse(user.getContent().contains(other));
        assertFalse(system.getContent().contains(own));
    }

    @SuppressWarnings("unchecked")
    private static List<ModelRequest> anyList() {
        return any(List.class);
    }
}

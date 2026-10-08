package com.xmut.forma.extension.tool.common.excerpt.port;

import com.xmut.forma.extension.tool.common.excerpt.ChunkExcerpt;
import com.xmut.forma.extension.tool.common.excerpt.ModelChunkExcerpter;
import com.xmut.forma.extension.tool.common.excerpt.TechDigestChunk;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChunkExcerptPort is the 摘句 atom: chunks → excerpts, same contract as ModelChunkExcerpter.
 */
class ChunkExcerptPortTest {

    @Test
    void modelExcerpter_isChunkExcerptPort_andReturnsQuotesPerChunk() {
        ModelProvider model = new ModelProvider() {
            @Override
            public ModelResponse complete(ModelRequest request) {
                throw new AssertionError("complete must not be used");
            }

            @Override
            public List<ModelResponse> completeBatch(List<ModelRequest> requests) {
                return Arrays.asList(
                        ModelResponse.builder().content("{\"quotes\":[\"第一句。\"]}").build(),
                        ModelResponse.builder().content("{\"quotes\":[\"第二句。\"]}").build());
            }
        };

        ChunkExcerptPort port = new ModelChunkExcerpter(model);
        List<ChunkExcerpt> excerpts = port.excerpt(Arrays.asList(
                new TechDigestChunk("发布", "第一句。尾巴甲。"),
                new TechDigestChunk("细节", "第二句。尾巴乙。")));

        assertEquals(2, excerpts.size());
        assertEquals("发布", excerpts.get(0).getHeading());
        assertEquals(Collections.singletonList("第一句。"), excerpts.get(0).getQuotes());
        assertEquals("细节", excerpts.get(1).getHeading());
        assertEquals(Collections.singletonList("第二句。"), excerpts.get(1).getQuotes());
        assertTrue(port instanceof ModelChunkExcerpter);
    }
}

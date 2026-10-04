package com.xmut.forma.extension.tool.tech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.pi.ai.message.Message;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Asks the side model once, via {@link ModelProvider#completeBatch}, for quotes from each chunk.
 * Each request window contains only that chunk. Parse failures become empty quotes.
 */
public final class ModelChunkExcerpter {

    private static final Logger log = LoggerFactory.getLogger(ModelChunkExcerpter.class);

    public static final String USE_CASE = "forma.tech.excerpt";

    public static final String SYSTEM =
            "你只从本块原文复制值得留下的连续句子。只输出 JSON {\"quotes\":[\"...\"]}。不要改写、不要块外知识、不要解释。条数自定，可以是空数组。";

    private final ModelProvider modelProvider;
    private final ObjectMapper objectMapper;

    public ModelChunkExcerpter(ModelProvider modelProvider) {
        this(modelProvider, new ObjectMapper());
    }

    ModelChunkExcerpter(ModelProvider modelProvider, ObjectMapper objectMapper) {
        this.modelProvider = modelProvider;
        this.objectMapper = objectMapper;
    }

    public List<ChunkExcerpt> excerpt(List<TechDigestChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelRequest> requests = new ArrayList<ModelRequest>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TechDigestChunk chunk = chunks.get(i);
            if (chunk == null) {
                chunk = new TechDigestChunk("", "");
            }
            requests.add(ModelRequest.builder()
                    .useCase(USE_CASE)
                    .messages(Arrays.asList(Message.system(SYSTEM), Message.user(userContent(chunk))))
                    .temperature(0.0)
                    .maxTokens(1024)
                    .build());
        }
        List<ModelResponse> responses = invokeBatch(requests, chunks.size());
        List<ChunkExcerpt> out = new ArrayList<ChunkExcerpt>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TechDigestChunk chunk = chunks.get(i);
            String heading = chunk == null ? "" : chunk.getHeading();
            List<String> quotes = Collections.emptyList();
            if (responses != null && i < responses.size() && responses.get(i) != null) {
                quotes = parseQuotes(responses.get(i).getContent());
            }
            out.add(new ChunkExcerpt(heading, quotes));
        }
        return out;
    }

    private List<ModelResponse> invokeBatch(List<ModelRequest> requests, int chunkCount) {
        try {
            return modelProvider.completeBatch(requests);
        } catch (RuntimeException ex) {
            log.warn("excerpt completeBatch failed chunks={} : {}", chunkCount, ex.toString());
            return null;
        }
    }

    private List<String> parseQuotes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(stripFence(raw));
            if (root == null || !root.isObject() || !root.has("quotes") || !root.get("quotes").isArray()) {
                return Collections.emptyList();
            }
            List<String> quotes = new ArrayList<String>();
            for (JsonNode item : root.get("quotes")) {
                if (item != null && item.isTextual()) {
                    quotes.add(item.asText());
                }
            }
            return quotes;
        } catch (Exception ex) {
            log.warn("excerpt parse failed: {}", ex.toString());
            return Collections.emptyList();
        }
    }

    private static String userContent(TechDigestChunk chunk) {
        String heading = chunk.getHeading() == null ? "" : chunk.getHeading();
        String text = chunk.getText() == null ? "" : chunk.getText();
        if (heading.isEmpty()) {
            return text;
        }
        return heading + "\n\n" + text;
    }

    private static String stripFence(String raw) {
        String s = raw.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNl = s.indexOf('\n');
        if (firstNl < 0) {
            return s;
        }
        s = s.substring(firstNl + 1);
        int fence = s.lastIndexOf("```");
        if (fence >= 0) {
            s = s.substring(0, fence);
        }
        return s.trim();
    }
}

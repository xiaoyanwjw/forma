package com.xmut.forma.extension.tool.tech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.pi.agent.graph.node.ToolHandler;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.agent.tool.base.LocalFileSupport;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Legacy thin wrapper around {@link ChunkExcerptPort} (unit tests / helpers).
 * Not registered in the Agent tool catalog.
 */
public final class ExcerptChunksToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ExcerptChunksToolHandler.class);

    public static final String TOOL_NAME = "excerpt_chunks";
    static final String DEFAULT_SOURCE_PATH = "source.md";
    private static final int MAX_CALLER_CHUNKS = 12;

    private final ChunkExcerptPort excerptPort;
    private final ObjectMapper objectMapper;

    public ExcerptChunksToolHandler(ChunkExcerptPort excerptPort) {
        this(excerptPort, new ObjectMapper());
    }

    ExcerptChunksToolHandler(ChunkExcerptPort excerptPort, ObjectMapper objectMapper) {
        this.excerptPort = excerptPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolResult handle(ToolCallEntry call, ToolContext ctx) {
        String callId = call != null ? call.getId() : null;
        try {
            Prepared prepared = prepare(call, ctx);
            List<ChunkExcerpt> raw = excerptPort == null
                    ? Collections.<ChunkExcerpt>emptyList()
                    : excerptPort.excerpt(prepared.chunks);
            ObjectNode root = objectMapper.createObjectNode();
            root.put("ok", true);
            root.put("partialCoverage", prepared.partialCoverage);
            ArrayNode excerpts = root.putArray("excerpts");
            for (int i = 0; i < prepared.chunks.size(); i++) {
                TechDigestChunk chunk = prepared.chunks.get(i);
                String text = chunk == null ? "" : chunk.getText();
                String heading = chunk == null ? "" : chunk.getHeading();
                List<String> quotes = ExcerptQuoteHelper.sanitize(text, rawQuotesAt(raw, i));
                ObjectNode node = excerpts.addObject();
                node.put("heading", heading == null ? "" : heading);
                ArrayNode quoteNodes = node.putArray("quotes");
                for (int q = 0; q < quotes.size(); q++) {
                    quoteNodes.add(quotes.get(q));
                }
            }
            return ToolResult.ok(callId, TOOL_NAME, objectMapper.writeValueAsString(root));
        } catch (IllegalArgumentException ex) {
            log.warn("excerpt_chunks rejected path: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("excerpt_chunks rejected: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, ex.getMessage());
        } catch (Exception ex) {
            log.warn("excerpt_chunks failed: {}", ex.toString());
            return ToolResult.failed(callId, TOOL_NAME, "excerpt_chunks failed");
        }
    }

    private Prepared prepare(ToolCallEntry call, ToolContext ctx) throws Exception {
        JsonNode args = arguments(call);
        if (args != null) {
            JsonNode chunks = args.get("chunks");
            if (chunks != null && chunks.isArray() && chunks.size() > 0) {
                return prepareCallerChunks(chunks);
            }
            String text = textOrNull(args.get("text"));
            if (StringUtils.hasText(text)) {
                TechDigestPrepResult sliced = TechDigestSourcePrep.slice(text);
                return new Prepared(sliced.getChunks(), sliced.isPartialCoverage());
            }
        }
        String sourcePath = DEFAULT_SOURCE_PATH;
        if (args != null) {
            String given = textOrNull(args.get("sourcePath"));
            if (StringUtils.hasText(given)) {
                sourcePath = given.trim();
            }
        }
        String workspace = LocalFileSupport.workspace(ctx);
        if (workspace == null) {
            throw new IllegalStateException("workspace root missing");
        }

        Path path = LocalFileSupport.resolveUnder(Paths.get(workspace), sourcePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("file not found");
        }

        String body = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        TechDigestPrepResult sliced = TechDigestSourcePrep.slice(body);
        return new Prepared(sliced.getChunks(), sliced.isPartialCoverage());
    }

    private static Prepared prepareCallerChunks(JsonNode chunks) {
        List<TechDigestChunk> parsed = parseChunks(chunks);
        if (parsed.size() <= MAX_CALLER_CHUNKS) {
            return new Prepared(parsed, false);
        }
        return new Prepared(new ArrayList<TechDigestChunk>(parsed.subList(0, MAX_CALLER_CHUNKS)), true);
    }

    private static List<TechDigestChunk> parseChunks(JsonNode chunks) {
        List<TechDigestChunk> out = new ArrayList<TechDigestChunk>();
        for (JsonNode item : chunks) {
            if (item == null || !item.isObject()) {
                out.add(new TechDigestChunk("", ""));
                continue;
            }
            out.add(new TechDigestChunk(textOrEmpty(item.get("heading")), textOrEmpty(item.get("text"))));
        }
        return out;
    }

    private static List<String> rawQuotesAt(List<ChunkExcerpt> raw, int index) {
        if (raw == null || index < 0 || index >= raw.size() || raw.get(index) == null) {
            return Collections.emptyList();
        }
        List<String> quotes = raw.get(index).getQuotes();
        return quotes == null ? Collections.<String>emptyList() : quotes;
    }

    private static JsonNode arguments(ToolCallEntry call) {
        if (call == null || call.getArguments() == null || call.getArguments().isNull()) {
            return null;
        }
        return call.getArguments();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }

    private static String textOrEmpty(JsonNode node) {
        String text = textOrNull(node);
        return text == null ? "" : text;
    }

    private static final class Prepared {
        private final List<TechDigestChunk> chunks;
        private final boolean partialCoverage;

        private Prepared(List<TechDigestChunk> chunks, boolean partialCoverage) {
            this.chunks = chunks == null ? Collections.<TechDigestChunk>emptyList() : chunks;
            this.partialCoverage = partialCoverage;
        }
    }
}

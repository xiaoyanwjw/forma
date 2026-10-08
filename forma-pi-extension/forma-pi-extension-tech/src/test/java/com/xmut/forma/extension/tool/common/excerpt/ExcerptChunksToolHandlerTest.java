package com.xmut.forma.extension.tool.common.excerpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.forma.extension.config.common.ExcerptToolsConfiguration;
import com.xmut.forma.extension.tool.common.excerpt.port.ChunkExcerptPort;
import com.xmut.forma.pi.agent.tool.ToolContext;
import com.xmut.forma.pi.ai.model.ModelProvider;
import com.xmut.forma.pi.ai.model.ModelRequest;
import com.xmut.forma.pi.ai.model.ModelResponse;
import com.xmut.forma.pi.ai.tool.ToolCallEntry;
import com.xmut.forma.pi.ai.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcerptChunksToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CHUNK_A = "模型把句子改写了。这不是原文尾巴甲。";
    private static final String CHUNK_B = "原文子串必须保留。第二句尾巴乙不会出现。";

    @TempDir
    Path workspace;

    @Test
    void twoChunksDropRewriteKeepSubstringAndOmitFullText() throws Exception {
        ModelProvider model = new ScriptedModel(
                "{\"quotes\":[\"完全改写的另一句话\"]}",
                "{\"quotes\":[\"原文子串必须保留。\"]}");
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(new ModelChunkExcerpter(model));

        ToolResult result = handler.handle(chunksCall(CHUNK_A, CHUNK_B), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertFalse(root.get("partialCoverage").asBoolean());
        assertFalse(root.has("text"));
        assertFalse(root.has("chunks"));
        JsonNode excerpts = root.get("excerpts");
        assertEquals(2, excerpts.size());
        assertEquals("发布", excerpts.get(0).get("heading").asText());
        assertEquals("模型把句子改写了。", excerpts.get(0).get("quotes").get(0).asText());
        assertEquals(1, excerpts.get(0).get("quotes").size());
        assertFalse(excerpts.get(0).has("text"));
        assertEquals("细节", excerpts.get(1).get("heading").asText());
        assertEquals("原文子串必须保留。", excerpts.get(1).get("quotes").get(0).asText());
        assertFalse(excerpts.get(1).has("text"));
        assertFalse(result.getOutput().contains("这不是原文尾巴甲"));
        assertFalse(result.getOutput().contains("第二句尾巴乙不会出现"));
        assertFalse(result.getOutput().contains("完全改写的另一句话"));
        assertEquals(1, ((ScriptedModel) model).batchCalls.get());
        assertEquals(2, ((ScriptedModel) model).lastSize);
    }

    @Test
    void callerChunksCapAtTwelveAndMarkPartialWhenLonger() throws Exception {
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(null);
        ToolResult capped = handler.handle(numberedChunksCall(13), ctx());
        assertTrue(capped.isSuccess());
        JsonNode over = MAPPER.readTree(capped.getOutput());
        assertTrue(over.get("partialCoverage").asBoolean());
        assertEquals(12, over.get("excerpts").size());
        assertEquals("h0", over.get("excerpts").get(0).get("heading").asText());
        assertEquals("h11", over.get("excerpts").get(11).get("heading").asText());
        assertFalse(capped.getOutput().contains("句子12"));

        ToolResult exact = handler.handle(numberedChunksCall(12), ctx());
        JsonNode full = MAPPER.readTree(exact.getOutput());
        assertFalse(full.get("partialCoverage").asBoolean());
        assertEquals(12, full.get("excerpts").size());
    }

    @Test
    void chunksWinOverPastedText() throws Exception {
        ModelProvider model = new ScriptedModel("{\"quotes\":[\"块内句子。\"]}");
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(new ModelChunkExcerpter(model));
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("text", "粘贴正文不该被切。另一句。");
        ArrayNode chunks = args.putArray("chunks");
        ObjectNode one = chunks.addObject();
        one.put("heading", "块");
        one.put("text", "块内句子。");

        ToolResult result = handler.handle(new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, args), ctx());
        JsonNode quotes = MAPPER.readTree(result.getOutput()).get("excerpts").get(0).get("quotes");
        assertEquals("块内句子。", quotes.get(0).asText());
        assertFalse(result.getOutput().contains("粘贴正文不该被切"));
    }

    @Test
    void pastedTextSkipsFile() throws Exception {
        Files.write(workspace.resolve("source.md"), "文件句子。不该出现。".getBytes(StandardCharsets.UTF_8));
        ModelProvider model = new ScriptedModel("{\"quotes\":[\"直接粘贴的句子。\"]}");
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(new ModelChunkExcerpter(model));
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("text", "直接粘贴的句子。文件不该读。");

        ToolResult result = handler.handle(new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, args), ctx());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals("直接粘贴的句子。", root.get("excerpts").get(0).get("quotes").get(0).asText());
        assertFalse(result.getOutput().contains("文件句子"));
        assertFalse(result.getOutput().contains("文件不该读"));
    }

    @Test
    void readsDefaultSourceMdWithoutEchoingRest() throws Exception {
        Files.write(workspace.resolve("source.md"),
                "第一句来自文件。文件里的后半句不该出现。".getBytes(StandardCharsets.UTF_8));
        ModelProvider model = new ScriptedModel("{\"quotes\":[\"第一句来自文件。\"]}");
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(new ModelChunkExcerpter(model));

        ToolResult result = handler.handle(new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, null), ctx());

        assertTrue(result.isSuccess());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertFalse(root.get("partialCoverage").asBoolean());
        assertEquals("第一句来自文件。", root.get("excerpts").get(0).get("quotes").get(0).asText());
        assertFalse(result.getOutput().contains("文件里的后半句不该出现"));
        assertFalse(root.toString().contains("\"text\""));
    }

    @Test
    void missingModelFallsBackToFirstSentence() throws Exception {
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(null);
        ToolResult result = handler.handle(chunksCall("你好世界。后面不要。", "第二块开头。尾巴隐藏。"), ctx());
        JsonNode excerpts = MAPPER.readTree(result.getOutput()).get("excerpts");
        assertEquals("你好世界。", excerpts.get(0).get("quotes").get(0).asText());
        assertEquals("第二块开头。", excerpts.get(1).get("quotes").get(0).asText());
        assertFalse(result.getOutput().contains("后面不要"));
        assertFalse(result.getOutput().contains("尾巴隐藏"));
    }

    @Test
    void missingSourceFileFailsClosed() {
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(null);
        ToolResult result = handler.handle(
                new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, null), ctx());
        assertFalse(result.isSuccess());
        assertEquals("file not found", result.getErrorMessage());
    }

    @Test
    void rejectsPathEscape() {
        ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(null);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("sourcePath", "../secret.md");
        ToolResult result = handler.handle(new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, args), ctx());
        assertFalse(result.isSuccess());
        assertFalse(result.getOutput() != null && result.getOutput().contains("secret"));
    }

    @Test
    void springConfigWithoutModelUsesGuard() {
        new ApplicationContextRunner()
                .withUserConfiguration(ExcerptToolsConfiguration.class)
                .run(context -> {
                    ChunkExcerptPort port = context.getBean(ChunkExcerptPort.class);
                    ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(port);
                    ToolResult result = handler.handle(chunksCall("你好世界。后面不要。", CHUNK_B), ctx());
                    JsonNode quotes = MAPPER.readTree(result.getOutput()).get("excerpts").get(0).get("quotes");
                    assertEquals("你好世界。", quotes.get(0).asText());
                });
    }

    @Test
    void springConfigWithStubModelUsesBatchQuote() {
        new ApplicationContextRunner()
                .withUserConfiguration(ExcerptToolsConfiguration.class, StubModelConfig.class)
                .run(context -> {
                    ChunkExcerptPort port = context.getBean(ChunkExcerptPort.class);
                    ExcerptChunksToolHandler handler = new ExcerptChunksToolHandler(port);
                    ToolResult result = handler.handle(chunksCall(
                            "第一句保底。第二句才是摘录。",
                            CHUNK_B), ctx());
                    JsonNode quotes = MAPPER.readTree(result.getOutput()).get("excerpts").get(0).get("quotes");
                    assertEquals("第二句才是摘录。", quotes.get(0).asText());
                    assertFalse(result.getOutput().contains("第一句保底"));
                });
    }

    @Configuration
    static class StubModelConfig {
        @Bean
        ModelProvider modelProvider() {
            return new ModelProvider() {
                @Override
                public ModelResponse complete(ModelRequest request) {
                    throw new AssertionError("complete must not be used for excerpt chunks");
                }

                @Override
                public List<ModelResponse> completeBatch(List<ModelRequest> requests) {
                    return Collections.singletonList(
                            ModelResponse.builder().content("{\"quotes\":[\"第二句才是摘录。\"]}").build());
                }
            };
        }
    }

    private ToolContext ctx() {
        return new ToolContext("r1", "t1", null, workspace.toString());
    }

    private static ToolCallEntry numberedChunksCall(int count) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ArrayNode chunks = args.putArray("chunks");
        for (int i = 0; i < count; i++) {
            ObjectNode one = chunks.addObject();
            one.put("heading", "h" + i);
            one.put("text", "句子" + i + "。");
        }
        return new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, args);
    }

    private static ToolCallEntry chunksCall(String a, String b) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ArrayNode chunks = args.putArray("chunks");
        ObjectNode first = chunks.addObject();
        first.put("heading", "发布");
        first.put("text", a);
        ObjectNode second = chunks.addObject();
        second.put("heading", "细节");
        second.put("text", b);
        return new ToolCallEntry("c1", ExcerptChunksToolHandler.TOOL_NAME, args);
    }

    private static final class ScriptedModel implements ModelProvider {
        private final String[] contents;
        private final AtomicInteger batchCalls = new AtomicInteger();
        private int lastSize;

        private ScriptedModel(String... contents) {
            this.contents = contents;
        }

        @Override
        public ModelResponse complete(ModelRequest request) {
            throw new AssertionError("complete must not be used for excerpt chunks");
        }

        @Override
        public List<ModelResponse> completeBatch(List<ModelRequest> requests) {
            batchCalls.incrementAndGet();
            lastSize = requests == null ? 0 : requests.size();
            List<ModelResponse> out = new java.util.ArrayList<ModelResponse>();
            int n = requests == null ? 0 : requests.size();
            for (int i = 0; i < n; i++) {
                String content = i < contents.length ? contents[i] : "{\"quotes\":[]}";
                out.add(ModelResponse.builder().content(content).build());
            }
            return out;
        }
    }
}

package com.xmut.ebus.application.business.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationOutputParserTest {

    private GenerationOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new GenerationOutputParser(new ObjectMapper());
    }

    @Test
    void dualTrack_extractsViewAndArtifactWithoutValidatingItems() {
        String raw = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[]},"
                + "\"artifact\":{\"items\":[{\"title\":\"only-one\"}],\"disclaimer\":\"x\"}}";
        ParsedGenerationOutput out = parser.parse(raw);
        assertNotNull(out.getRawView());
        assertEquals(1, out.getRawView().get("version"));
        List<?> items = (List<?>) out.getBusinessPayload().get("items");
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertEquals("only-one", first.get("title"));
    }

    @Test
    void viewOnly_json_extractsRawViewAndEmptyBusinessPayload() {
        ParsedGenerationOutput out = parser.parse("{\"view\":{\"version\":1,\"blocks\":[]}}");
        assertNotNull(out.getRawView());
        assertEquals(1, out.getRawView().get("version"));
        assertTrue(out.getBusinessPayload().isEmpty());
    }

    @Test
    void artifactNonObject_emptyPayloadStillExtractsView() {
        String raw = "{\"view\":{\"version\":1,\"blocks\":[]},\"artifact\":\"not-an-object\"}";
        ParsedGenerationOutput out = parser.parse(raw);
        assertNotNull(out.getRawView());
        assertEquals(1, out.getRawView().get("version"));
        assertTrue(out.getBusinessPayload().isEmpty());
    }

    @Test
    void plainText_rawViewNull_payloadHasText() {
        ParsedGenerationOutput out = parser.parse("你好，这是草稿");
        assertNull(out.getRawView());
        assertEquals("你好，这是草稿", out.getBusinessPayload().get("text"));
    }

  @Test
  void fencedJson_supported() {
      ParsedGenerationOutput out = parser.parse("```json\n{\"view\":{\"version\":1,\"blocks\":[]},\"artifact\":{}}\n```");
      assertNotNull(out.getRawView());
      assertTrue(out.getBusinessPayload().isEmpty() || out.getBusinessPayload() != null);
  }

  @Test
  void proseThenFinalFence_prefersLastJsonWithView() {
      String raw = "I'll load the skill first.\nI'll search once.\n"
              + "```json\n{\"view\":{\"version\":1,\"title\":\"Mac Mini 配件\",\"blocks\":[]},\"artifact\":{\"items\":[]}}\n```";
      ParsedGenerationOutput out = parser.parse(raw);
      assertNotNull(out.getRawView());
      assertEquals("Mac Mini 配件", out.getRawView().get("title"));
  }

  @Test
  void prefersLastValidFencedJsonWhenEarlierFenceIsBroken() {
      String raw = "```json\n{\"view\":{\"version\":1,\"title\":\"broken\",\"blocks\":[\n```\n"
              + "```json\n{\"view\":{\"version\":1,\"title\":\"完整清单\",\"blocks\":[]},\"artifact\":{}}\n```";
      ParsedGenerationOutput out = parser.parse(raw);
      assertNotNull(out.getRawView());
      assertEquals("完整清单", out.getRawView().get("title"));
  }

  @Test
  void truncatedJson_returnsNullRawView() {
      String raw = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[{\"type\":\"list\",\"items\":[";
      ParsedGenerationOutput out = parser.parse(raw);
      assertNull(out.getRawView());
  }

    @Test
    void outputPointer_viewJson_loadsSiblingArtifact() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        Files.write(run.resolve("artifact.json"),
                "{\"title\":\"选题\",\"items\":[{\"id\":\"tp-1\"}]}".getBytes(StandardCharsets.UTF_8));
        Files.write(run.resolve("view.json"),
                ("{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}")
                        .getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse("{\"output\":\"view.json\"}", run);
        assertEquals(2, ((Number) out.getRawView().get("version")).intValue());
        assertEquals("html", out.getRawView().get("format"));
        assertEquals("选题", out.getBusinessPayload().get("title"));
    }

    @Test
    void outputPointer_viewJsonV2_missingSiblingArtifact_throws() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        Files.write(run.resolve("view.json"),
                ("{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}")
                        .getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"output\":\"view.json\"}", run));
        assertTrue(ex.getMessage().contains("artifact file missing"));
    }

    @Test
    void outputPointer_planView_loadsPlanArtifact() throws Exception {
        Path plan = Files.createTempDirectory("parse-ws-").resolve("plan");
        Files.createDirectories(plan);
        Files.write(plan.resolve("artifact.json"), "{\"title\":\"策划\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(plan.resolve("view.json"),
                "{\"version\":2,\"title\":\"策划\",\"format\":\"html\",\"content\":\"<p>p</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse("{\"output\":\"plan/view.json\"}", plan.getParent());
        assertEquals("策划", out.getBusinessPayload().get("title"));
    }

    @Test
    void outputPointer_readsFileForDualTrack() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        String body = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[]},\"artifact\":{\"items\":[]}}";
        Files.write(run.resolve("final.json"), body.getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse("{\"output\":\"final.json\"}", run);
        assertNotNull(out.getRawView());
        assertEquals("t", out.getRawView().get("title"));
    }

    @Test
    void outputPointer_missingFile_throws() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"output\":\"nope.json\"}", run));
        assertTrue(ex.getMessage().contains("output file missing"));
    }

    @Test
    void inlineWithoutPointer_unchanged() throws Exception {
        ParsedGenerationOutput out = parser.parse(
                "{\"view\":{\"version\":1,\"blocks\":[]},\"artifact\":{}}",
                Files.createTempDirectory("x"));
        assertNotNull(out.getRawView());
    }

    @Test
    void outputPointer_nullWorkspace_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("{\"output\":\"final.json\"}", null));
        assertEquals("workspace required for output pointer", ex.getMessage());
    }

    @Test
    void outputPointer_ignoresRootViewAndFollowsFileOnly() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        String body = "{\"view\":{\"version\":1,\"title\":\"from-disk\",\"blocks\":[]},\"artifact\":{\"items\":[]}}";
        Files.write(run.resolve("final.json"), body.getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse(
                "{\"output\":\"final.json\",\"view\":{\"version\":1,\"title\":\"from-root\",\"blocks\":[]}}",
                run);
        assertEquals("from-disk", out.getRawView().get("title"));
    }

    @Test
    void outputPointer_nestedPointerInFile_ignored() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        Files.write(run.resolve("inner.json"),
                "{\"view\":{\"version\":1,\"title\":\"nested\",\"blocks\":[]},\"artifact\":{}}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(run.resolve("outer.json"),
                "{\"output\":\"inner.json\",\"view\":{\"version\":1,\"title\":\"outer-file\",\"blocks\":[]}}"
                        .getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse("{\"output\":\"outer.json\"}", run);
        assertEquals("outer-file", out.getRawView().get("title"));
    }

    @Test
    void parseString_stillIgnoresOutputPointer() {
        ParsedGenerationOutput out = parser.parse("{\"output\":\"final.json\"}");
        assertNull(out.getRawView());
        assertEquals("final.json", out.getBusinessPayload().get("output"));
    }
}

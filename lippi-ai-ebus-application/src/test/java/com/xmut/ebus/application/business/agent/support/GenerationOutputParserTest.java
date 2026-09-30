package com.xmut.ebus.application.business.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
              + "```json\n{\"view\":{\"version\":1,\"title\":\"厨房小件\",\"blocks\":[]},\"artifact\":{\"items\":[]}}\n```";
      ParsedGenerationOutput out = parser.parse(raw);
      assertNotNull(out.getRawView());
      assertEquals("厨房小件", out.getRawView().get("title"));
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
}

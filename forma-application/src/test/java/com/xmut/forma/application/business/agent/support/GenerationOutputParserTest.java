package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationOutputParserTest {

    private GenerationOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new GenerationOutputParser();
    }

    @Test
    void supportsAlwaysTrue() {
        assertTrue(parser.supports(null));
        assertTrue(parser.supports(OutputParseContext.builder().build()));
        assertTrue(parser.supports(OutputParseContext.builder()
                .skillId("ecommerce-picklist")
                .finalResponse("x")
                .build()));
    }

    @Test
    void orderIsLowestPrecedence() {
        Order order = GenerationOutputParser.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(Ordered.LOWEST_PRECEDENCE, order.value());
    }

    @Test
    void blankFinal_rawViewNull_textPayloadEmpty_doesNotThrowMissingFile() {
        ParsedGenerationOutput blank = parser.parse(OutputParseContext.builder()
                .finalResponse("  \n")
                .build());
        assertNull(blank.getRawView());
        assertEquals("", blank.getBusinessPayload().get("text"));

        ParsedGenerationOutput missing = parser.parse(OutputParseContext.builder().build());
        assertNull(missing.getRawView());
        assertEquals("", missing.getBusinessPayload().get("text"));
        assertNull(parser.parse((OutputParseContext) null).getRawView());
    }

    @Test
    void text_rawViewNull_payloadIsTrimmedText() {
        ParsedGenerationOutput out = parser.parse(OutputParseContext.builder()
                .finalResponse("  你好，这是草稿  ")
                .build());
        assertNull(out.getRawView());
        assertEquals("你好，这是草稿", out.getBusinessPayload().get("text"));
        assertEquals(1, out.getBusinessPayload().size());
    }

    @Test
    void jsonWithView_staysText_doesNotExtractViewOrArtifact() {
        String raw = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[]},"
                + "\"artifact\":{\"items\":[{\"title\":\"only-one\"}]}}";
        ParsedGenerationOutput out = parser.parse(OutputParseContext.builder()
                .finalResponse("```json\n" + raw + "\n```")
                .skillId("ecommerce-picklist")
                .build());
        assertNull(out.getRawView());
        assertEquals("```json\n" + raw + "\n```", out.getBusinessPayload().get("text"));
        assertFalse(out.getBusinessPayload().containsKey("items"));
        assertFalse(out.getBusinessPayload().containsKey("view"));
    }

    @Test
    void pointerText_doesNotReadWorkspace() throws Exception {
        Path run = Files.createTempDirectory("parse-ws-");
        Files.write(run.resolve("view.json"),
                "{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}"
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(run.resolve("artifact.json"),
                "{\"title\":\"选题\"}".getBytes(StandardCharsets.UTF_8));
        ParsedGenerationOutput out = parser.parse(OutputParseContext.builder()
                .finalResponse("{\"output\":\"view.json\"}")
                .runWorkspace(run)
                .build());
        assertNull(out.getRawView());
        assertEquals("{\"output\":\"view.json\"}", out.getBusinessPayload().get("text"));
        assertFalse(out.getBusinessPayload().containsKey("title"));
    }
}

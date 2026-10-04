package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.common.output.OutputParseContext;
import com.xmut.forma.common.output.OutputParser;
import com.xmut.forma.common.output.ParsedGenerationOutput;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputParserCompositeTest {

    @Test
    void springWiresCompositeAndFallsBackToText() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                GenerationOutputParser.class, OutputParserComposite.class);
        try {
            OutputParserComposite composite = ctx.getBean(OutputParserComposite.class);
            ParsedGenerationOutput out = composite.parse(OutputParseContext.builder()
                    .finalResponse("  你好  ")
                    .build());
            assertNull(out.getRawView());
            assertEquals("你好", out.getBusinessPayload().get("text"));
        } finally {
            ctx.close();
        }
    }

    @Test
    void prefersLowerOrderEvenWhenListedLater() {
        OrderedParser laterWins = new OrderedParser(0, true, "workspace");
        OrderedParser listedFirst = new OrderedParser(10, true, "other");
        OutputParserComposite composite = new OutputParserComposite(
                Arrays.<OutputParser>asList(listedFirst, laterWins),
                new GenerationOutputParser());

        ParsedGenerationOutput out = composite.parse(OutputParseContext.builder()
                .skillId("ecommerce-picklist")
                .finalResponse("ignored")
                .build());

        assertEquals("workspace", out.getBusinessPayload().get("text"));
        assertEquals(1, laterWins.parses);
        assertEquals(0, listedFirst.parses);
    }

    @Test
    void applyingParserExceptionIsNotSwallowed() {
        OutputParser missingFile = new OutputParser() {
            @Override
            public boolean appliesTo(OutputParseContext ctx) {
                return true;
            }

            @Override
            public ParsedGenerationOutput parse(OutputParseContext ctx) {
                throw new IllegalArgumentException("output file missing: view.json");
            }
        };
        OutputParserComposite composite = new OutputParserComposite(
                Collections.singletonList(missingFile),
                new GenerationOutputParser());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                composite.parse(OutputParseContext.builder()
                        .skillId("ecommerce-picklist")
                        .finalResponse("终稿还在")
                        .build()));
        assertTrue(ex.getMessage().contains("output file missing"));
    }

    @Test
    void whenNoParserApplies_fallsBackToText() {
        OutputParser never = new OutputParser() {
            @Override
            public boolean appliesTo(OutputParseContext ctx) {
                return false;
            }

            @Override
            public ParsedGenerationOutput parse(OutputParseContext ctx) {
                throw new AssertionError("must not parse");
            }
        };
        OutputParserComposite composite = new OutputParserComposite(
                Collections.singletonList(never),
                new GenerationOutputParser());

        ParsedGenerationOutput out = composite.parse(OutputParseContext.builder()
                .finalResponse("闲聊")
                .build());
        assertNull(out.getRawView());
        assertEquals("闲聊", out.getBusinessPayload().get("text"));
    }

    @Test
    void idleChatWithoutSkillOrViewFile_returnsTextPayload() throws Exception {
        Path root = Files.createTempDirectory("idle-chat-");
        GenerationOutputParser fallback = new GenerationOutputParser();
        OutputParserComposite composite = new OutputParserComposite(
                Collections.<OutputParser>singletonList(fallback),
                fallback);

        ParsedGenerationOutput out = composite.parse(OutputParseContext.builder()
                .finalResponse("  随便聊聊  ")
                .workspaceRoot(root)
                .build());

        assertNull(out.getRawView());
        assertEquals("随便聊聊", out.getBusinessPayload().get("text"));
        assertFalse(Files.exists(root.resolve("view.json")));
    }

    @Test
    void skipsNullAndSelf() throws Exception {
        GenerationOutputParser fallback = new GenerationOutputParser();
        OutputParserComposite composite = new OutputParserComposite(
                Arrays.asList(null, fallback), fallback);
        Field field = OutputParserComposite.class.getDeclaredField("parsers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<OutputParser> held = (List<OutputParser>) field.get(composite);
        held.add(0, composite);

        ParsedGenerationOutput out = composite.parse(OutputParseContext.builder()
                .finalResponse("你好")
                .build());
        assertEquals("你好", out.getBusinessPayload().get("text"));
        assertNull(out.getRawView());
    }

    private static ParsedGenerationOutput text(String value) {
        return new ParsedGenerationOutput(null, Collections.<String, Object>singletonMap("text", value));
    }

    private static final class OrderedParser implements OutputParser, Ordered {
        private final int order;
        private final boolean applies;
        private final String label;
        private int parses;

        private OrderedParser(int order, boolean applies, String label) {
            this.order = order;
            this.applies = applies;
            this.label = label;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public boolean appliesTo(OutputParseContext ctx) {
            return applies;
        }

        @Override
        public ParsedGenerationOutput parse(OutputParseContext ctx) {
            parses++;
            return text(label);
        }
    }
}

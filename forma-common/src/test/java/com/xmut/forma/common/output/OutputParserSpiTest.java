package com.xmut.forma.common.output;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputParserSpiTest {

    @Test
    void contextBuilderAndConstructorExposeFields() {
        Path root = Paths.get("/tmp/run");
        Map<String, Object> exec = new HashMap<String, Object>();
        exec.put("viewPath", "exec/view.json");
        exec.put("artifactPath", "exec/artifact.json");
        TurnAttachment attachment = TurnAttachment.of(exec);
        OutputParseContext built = OutputParseContext.builder()
                .skillId("ecommerce-skulist")
                .sceneCode("ecommerce")
                .resumeOptionId("confirm_execute")
                .finalResponse("done")
                .workspaceRoot(root)
                .attachment(attachment)
                .build();
        OutputParseContext constructed = new OutputParseContext(
                "ecommerce-skulist", "ecommerce", "confirm_execute", "done", root,
                attachment);

        assertEquals(constructed, built);
        assertEquals("ecommerce-skulist", built.getSkillId());
        assertEquals("ecommerce", built.getSceneCode());
        assertEquals("confirm_execute", built.getResumeOptionId());
        assertEquals("done", built.getFinalResponse());
        assertEquals(root, built.getWorkspaceRoot());
        assertEquals("exec/view.json", built.getAttachment().get("viewPath"));
        assertEquals("exec/artifact.json", built.getAttachment().get("artifactPath"));
    }

    @Test
    void contextAllowsNulls() {
        OutputParseContext ctx = OutputParseContext.builder().build();
        assertNull(ctx.getSkillId());
        assertNull(ctx.getSceneCode());
        assertNull(ctx.getResumeOptionId());
        assertNull(ctx.getFinalResponse());
        assertNull(ctx.getWorkspaceRoot());
        assertEquals(TurnAttachment.empty(), ctx.getAttachment());
        assertTrue(ctx.getAttachment().isEmpty());
    }

    @Test
    void parserAppliesThenParses() {
        OutputParser parser = new OutputParser() {
            @Override
            public boolean appliesTo(OutputParseContext ctx) {
                return ctx != null && "chat".equals(ctx.getSkillId());
            }

            @Override
            public ParsedGenerationOutput parse(OutputParseContext ctx) {
                return new ParsedGenerationOutput(null,
                        Collections.<String, Object>singletonMap("text", ctx.getFinalResponse()));
            }
        };
        OutputParseContext hit = OutputParseContext.builder()
                .skillId("chat")
                .finalResponse("hi")
                .build();
        OutputParseContext miss = OutputParseContext.builder().skillId("other").build();

        assertTrue(parser.appliesTo(hit));
        assertFalse(parser.appliesTo(miss));
        ParsedGenerationOutput out = parser.parse(hit);
        assertNull(out.getRawView());
        assertEquals("hi", out.getBusinessPayload().get("text"));
    }

    @Test
    void parsedOutputRejectsMutationAndNullPayload() {
        Map<String, Object> view = new HashMap<String, Object>();
        view.put("version", 1);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("title", "t");
        ParsedGenerationOutput out = new ParsedGenerationOutput(view, payload);

        assertEquals(Integer.valueOf(1), out.getRawView().get("version"));
        assertEquals("t", out.getBusinessPayload().get("title"));
        assertThrows(UnsupportedOperationException.class, () -> out.getRawView().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> out.getBusinessPayload().put("x", 1));

        ParsedGenerationOutput empty = new ParsedGenerationOutput(null, null);
        assertNull(empty.getRawView());
        assertTrue(empty.getBusinessPayload().isEmpty());
    }
}

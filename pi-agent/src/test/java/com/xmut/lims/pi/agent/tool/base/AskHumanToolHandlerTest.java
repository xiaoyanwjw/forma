package com.xmut.lims.pi.agent.tool.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.tool.ToolContext;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AskHumanToolHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AskHumanToolHandler handler = new AskHumanToolHandler();

    @Test
    void handleEchoesQuestionAndOptions() throws Exception {
        ToolResult result = handler.handle(listingAskCall("call-1"), new ToolContext("run-1", "t1"));
        assertTrue(result.isSuccess());
        assertTrue(result.isInterrupt());
        assertEquals(AskHumanToolHandler.TOOL_NAME, result.getToolName());
        JsonNode root = MAPPER.readTree(result.getOutput());
        assertEquals("策划可以了吗？确认后写出执行稿，或补充需求。", root.get("question").asText());
        assertTrue(root.get("allowFreeText").asBoolean());
        assertEquals("confirm_execute", root.get("options").get(0).get("id").asText());
        assertEquals("supplement", root.get("options").get(1).get("id").asText());
    }

    @Test
    void parseOutput_readsNormalizedHandlerJson() throws Exception {
        ToolResult result = handler.handle(listingAskCall("call-1"), new ToolContext("run-1", "t1"));
        AskHumanToolHandler.ParsedAsk parsed = AskHumanToolHandler.parseOutput(result.getOutput());
        assertEquals("策划可以了吗？确认后写出执行稿，或补充需求。", parsed.getQuestion());
        assertEquals("confirm_execute", parsed.getOptions().get(0).get("id"));
    }

    @Test
    void parseRejectsMissingQuestion() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        ArrayNode options = args.putArray("options");
        ObjectNode opt = options.addObject();
        opt.put("id", "confirm_execute");
        opt.put("label", "确认，出执行稿");
        assertNull(AskHumanToolHandler.parse(new ToolCallEntry("c", AskHumanToolHandler.TOOL_NAME, args)));
    }

    @Test
    void handleFailsWithoutOptions() {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("question", "只问一句");
        ToolResult result = handler.handle(
                new ToolCallEntry("c2", AskHumanToolHandler.TOOL_NAME, args),
                new ToolContext("r1", "t1"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("options"));
    }

    public static ToolCallEntry listingAskCall(String callId) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("question", "策划可以了吗？确认后写出执行稿，或补充需求。");
        args.put("allowFreeText", true);
        ArrayNode options = args.putArray("options");
        ObjectNode confirm = options.addObject();
        confirm.put("id", "confirm_execute");
        confirm.put("label", "确认，出执行稿");
        ObjectNode supplement = options.addObject();
        supplement.put("id", "supplement");
        supplement.put("label", "补充需求");
        return new ToolCallEntry(callId, AskHumanToolHandler.TOOL_NAME, args);
    }
}

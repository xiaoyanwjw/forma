package com.xmut.forma.application.business.agent.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmut.lims.pi.agent.tool.base.AskHumanToolHandler;
import com.xmut.lims.pi.ai.tool.ToolCallEntry;

/** Shared ask_human fixture for ebus tests after the handler moved to pi-agent. */
public final class AskHumanListingAsk {

    private AskHumanListingAsk() {
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

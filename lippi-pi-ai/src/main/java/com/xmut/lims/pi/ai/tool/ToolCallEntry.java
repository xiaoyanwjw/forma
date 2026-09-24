package com.xmut.lims.pi.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次待执行的工具调用（hermes 自有；禁止复用 agent ToolCallEntry）。
 */
public final class ToolCallEntry {

    private final String id;
    private final String toolName;
    private final JsonNode arguments;

    public ToolCallEntry(String id, String toolName, JsonNode arguments) {
        this.id = id;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public JsonNode getArguments() {
        return arguments;
    }
}

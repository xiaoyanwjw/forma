package com.xmut.lims.pi.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolCallEntry)) {
            return false;
        }
        ToolCallEntry that = (ToolCallEntry) o;
        return Objects.equals(id, that.id)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, toolName, arguments);
    }

    @Override
    public String toString() {
        return "ToolCallEntry{id='" + id + "', toolName='" + toolName + "'}";
    }
}

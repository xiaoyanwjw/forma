package com.xmut.forma.domain.business.agent.model;

/**
 * 回放用 toolCalls 摘要（不含 arguments，避免把工具入参铺到浏览器）。
 */
public final class PiToolCallRef {

    private final String id;
    private final String toolName;

    public PiToolCallRef(String id, String toolName) {
        this.id = id;
        this.toolName = toolName;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }
}

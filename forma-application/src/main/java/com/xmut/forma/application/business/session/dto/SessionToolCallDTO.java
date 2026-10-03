package com.xmut.forma.application.business.session.dto;

/**
 * 会话回放中的 toolCall 摘要（无 arguments）。
 */
public class SessionToolCallDTO {

    private String id;
    private String toolName;

    public SessionToolCallDTO() {
    }

    public SessionToolCallDTO(String id, String toolName) {
        this.id = id;
        this.toolName = toolName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
}

package com.xmut.ebus.interfaces.vo.business.agent;

/**
 * {@code POST /api/v1/agent/runs/{runId}/resume} 入参。
 */
public class ResumeGenerationRunRequest {

    private String toolCallId;
    private String optionId;
    private String freeText;
    private String confirmRequestId;

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getFreeText() {
        return freeText;
    }

    public void setFreeText(String freeText) {
        this.freeText = freeText;
    }

    public String getConfirmRequestId() {
        return confirmRequestId;
    }

    public void setConfirmRequestId(String confirmRequestId) {
        this.confirmRequestId = confirmRequestId;
    }
}

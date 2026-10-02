package com.xmut.ebus.application.business.session.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 历史回放行（user / assistant / tool；tool 元数据供「过程」还原）。
 */
public class SessionMessageDTO {

    private String role;
    private String content;
    private Instant createdAt;
    /** pi_session_entry.seq；分页游标 */
    private Long seq;
    private String toolCallId;
    private List<SessionToolCallDTO> toolCalls = Collections.emptyList();
    /**
     * 落库 append 键；HITL 可能带 {@code :suspend}/{@code :resume} 后缀。
     * FE 按逻辑 runId 把策划+执行收成一轮。
     */
    private String runId;

    public SessionMessageDTO() {
    }

    public SessionMessageDTO(String role, String content, Instant createdAt, Long seq) {
        this(role, content, createdAt, seq, null, null, null);
    }

    public SessionMessageDTO(
            String role,
            String content,
            Instant createdAt,
            Long seq,
            String toolCallId,
            List<SessionToolCallDTO> toolCalls) {
        this(role, content, createdAt, seq, toolCallId, toolCalls, null);
    }

    public SessionMessageDTO(
            String role,
            String content,
            Instant createdAt,
            Long seq,
            String toolCallId,
            List<SessionToolCallDTO> toolCalls,
            String runId) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.seq = seq;
        this.toolCallId = toolCallId;
        setToolCalls(toolCalls);
        this.runId = runId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public List<SessionToolCallDTO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<SessionToolCallDTO> toolCalls) {
        this.toolCalls = toolCalls == null || toolCalls.isEmpty()
                ? Collections.<SessionToolCallDTO>emptyList()
                : Collections.unmodifiableList(new ArrayList<SessionToolCallDTO>(toolCalls));
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}

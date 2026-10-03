package com.xmut.forma.domain.business.agent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * pi_session_entry 只读投影行（回放：role/content + 可选 tool 元数据 + runId + created_at + seq）。
 * 非 HTTP/Application 出口 DTO。
 */
public final class PiMessage {

    private final String role;
    private final String content;
    private final Instant createdAt;
    private final long seq;
    /** tool 角色：对应 assistant.toolCalls[].id */
    private final String toolCallId;
    /** assistant 角色：本轮发起的工具调用摘要 */
    private final List<PiToolCallRef> toolCalls;
    /**
     * 落库 append 键；HITL 可能为 {@code runId:suspend} / {@code runId:resume}。
     * 回放按去后缀后的逻辑 runId 聚类。
     */
    private final String runId;

    public PiMessage(String role, String content, Instant createdAt, long seq) {
        this(role, content, createdAt, seq, null, Collections.<PiToolCallRef>emptyList(), null);
    }

    public PiMessage(
            String role,
            String content,
            Instant createdAt,
            long seq,
            String toolCallId,
            List<PiToolCallRef> toolCalls) {
        this(role, content, createdAt, seq, toolCallId, toolCalls, null);
    }

    public PiMessage(
            String role,
            String content,
            Instant createdAt,
            long seq,
            String toolCallId,
            List<PiToolCallRef> toolCalls,
            String runId) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.seq = seq;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls == null || toolCalls.isEmpty()
                ? Collections.<PiToolCallRef>emptyList()
                : Collections.unmodifiableList(new ArrayList<PiToolCallRef>(toolCalls));
        this.runId = runId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getSeq() {
        return seq;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public List<PiToolCallRef> getToolCalls() {
        return toolCalls;
    }

    public String getRunId() {
        return runId;
    }
}

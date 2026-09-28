package com.xmut.ebus.domain.business.agent.model;

import java.time.Instant;

/**
 * pi_session_entry 投影行（回放用：role/content + created_at + seq）。
 */
public final class PiMessageDTO {

    private final String role;
    private final String content;
    private final Instant createdAt;
    private final long seq;

    public PiMessageDTO(String role, String content, Instant createdAt, long seq) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.seq = seq;
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
}

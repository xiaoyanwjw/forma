package com.xmut.ebus.application.business.session.dto;

import java.time.Instant;

/**
 * 历史回放气泡（R1：user / assistant 文本）。
 */
public class SessionMessageDTO {

    private String role;
    private String content;
    private Instant createdAt;

    public SessionMessageDTO() {
    }

    public SessionMessageDTO(String role, String content, Instant createdAt) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
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
}

package com.xmut.ebus.application.business.feedback.dto;

import java.time.Instant;

/**
 * 反馈读出 DTO。
 */
public class FeedbackDTO {

    private String id;
    private String artifactId;
    private String tag;
    private String commentText;
    private Instant createdAt;

    public FeedbackDTO() {
    }

    public FeedbackDTO(String id, String artifactId, String tag, String commentText, Instant createdAt) {
        this.id = id;
        this.artifactId = artifactId;
        this.tag = tag;
        this.commentText = commentText;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

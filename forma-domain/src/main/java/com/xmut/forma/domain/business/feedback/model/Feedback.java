package com.xmut.forma.domain.business.feedback.model;

import java.time.Instant;

/**
 * 成果质量反馈聚合根（独立表；不写 CreditLedger）。
 */
public class Feedback {

    private String id;
    private String userId;
    private String artifactId;
    private String tag;
    private String commentText;
    private Instant createdAt;
    private Instant updatedAt;

    public static Feedback create(String id,
                                  String userId,
                                  String artifactId,
                                  String tag,
                                  String commentText,
                                  Instant now) {
        Feedback feedback = new Feedback();
        feedback.id = id;
        feedback.userId = userId;
        feedback.artifactId = artifactId;
        feedback.tag = tag;
        feedback.commentText = commentText;
        feedback.createdAt = now;
        feedback.updatedAt = now;
        return feedback;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

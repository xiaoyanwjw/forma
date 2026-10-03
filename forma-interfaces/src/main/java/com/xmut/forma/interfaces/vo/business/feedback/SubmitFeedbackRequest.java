package com.xmut.forma.interfaces.vo.business.feedback;

/**
 * 提交质量反馈 HTTP 入参。
 */
public class SubmitFeedbackRequest {

    private String artifactId;
    private String tag;
    private String commentText;

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
}

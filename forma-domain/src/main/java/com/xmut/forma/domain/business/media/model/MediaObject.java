package com.xmut.forma.domain.business.media.model;

import java.time.Instant;

/**
 * 媒体元数据（字节在对象存储；MySQL 仅存 id/objectKey/contentType 等）。
 */
public class MediaObject {

    private String id;
    private String userId;
    private String objectKey;
    private String contentType;
    private long sizeBytes;
    private Instant createdAt;

    public static MediaObject create(String id,
                                     String userId,
                                     String objectKey,
                                     String contentType,
                                     long sizeBytes,
                                     Instant now) {
        MediaObject media = new MediaObject();
        media.id = id;
        media.userId = userId;
        media.objectKey = objectKey;
        media.contentType = contentType;
        media.sizeBytes = sizeBytes;
        media.createdAt = now;
        return media;
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

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

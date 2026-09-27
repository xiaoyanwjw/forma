package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.repository.MediaObjectRepository;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO（S3 API）实现：字节进对象存储；元数据进 MySQL（无大字段）。
 */
public class MinioMediaStore implements MediaStore {

    private static final Logger log = LoggerFactory.getLogger(MinioMediaStore.class);

    static final String MSG_MEDIA_BUSY = "服务繁忙，请稍后重试";

    private final MinioClient minioClient;
    private final MediaObjectRepository mediaObjectRepository;
    private final Clock clock;
    private final String bucket;
    private final int presignExpirySeconds;

    public MinioMediaStore(MinioClient minioClient,
                           MediaObjectRepository mediaObjectRepository,
                           Clock clock,
                           String bucket,
                           int presignExpirySeconds) {
        this.minioClient = minioClient;
        this.mediaObjectRepository = mediaObjectRepository;
        this.clock = clock;
        this.bucket = bucket;
        this.presignExpirySeconds = presignExpirySeconds > 0 ? presignExpirySeconds : 3600;
    }

    @Override
    public MediaObject put(String userId, String contentType, byte[] bytes) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户 ID 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "媒体内容不能为空");
        }
        String type = StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
        String id = UUID.randomUUID().toString();
        String objectKey = "listing/" + userId.trim() + "/" + id + ".png";
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(type)
                    .build());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }
        Instant now = Instant.now(clock);
        MediaObject meta = MediaObject.create(id, userId.trim(), objectKey, type, bytes.length, now);
        try {
            mediaObjectRepository.save(meta);
        } catch (RuntimeException ex) {
            removeObjectQuietly(objectKey, "metadata save failed after putObject");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }
        return meta;
    }

    @Override
    public String issueReadUrl(String mediaObjectId) {
        MediaObject meta = findById(mediaObjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "媒体不存在"));
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(meta.getObjectKey())
                    .expiry(presignExpirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }
    }

    @Override
    public Optional<MediaObject> findById(String mediaObjectId) {
        return mediaObjectRepository.findById(mediaObjectId);
    }

    @Override
    public void delete(String mediaObjectId) {
        if (!StringUtils.hasText(mediaObjectId)) {
            return;
        }
        Optional<MediaObject> found = mediaObjectRepository.findById(mediaObjectId.trim());
        if (found.isPresent()) {
            removeObjectQuietly(found.get().getObjectKey(), "delete mediaObjectId=" + mediaObjectId);
            try {
                mediaObjectRepository.deleteById(mediaObjectId.trim());
            } catch (RuntimeException ex) {
                log.warn("Failed to delete media metadata mediaObjectId={}: {}",
                        mediaObjectId, ex.toString());
            }
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void removeObjectQuietly(String objectKey, String reason) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception cleanupEx) {
            log.warn("Orphan media object may remain key={} reason={}: {}",
                    objectKey, reason, cleanupEx.toString());
        }
    }
}

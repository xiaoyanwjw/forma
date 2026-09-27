package com.xmut.ebus.infrastructure.media;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 测试 / 本地默认：字节与元数据均在内存；签发 data URI 供 Computer 显示。
 */
public class InMemoryMediaStore implements MediaStore {

    private final Clock clock;
    private final ConcurrentMap<String, Stored> byId = new ConcurrentHashMap<String, Stored>();

    public InMemoryMediaStore(Clock clock) {
        this.clock = clock;
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
        String objectKey = "memory/" + userId.trim() + "/" + id;
        Instant now = Instant.now(clock);
        MediaObject meta = MediaObject.create(id, userId.trim(), objectKey, type, bytes.length, now);
        byId.put(id, new Stored(meta, bytes));
        return meta;
    }

    @Override
    public String issueReadUrl(String mediaObjectId) {
        Stored stored = require(mediaObjectId);
        String b64 = Base64.getEncoder().encodeToString(stored.bytes);
        return "data:" + stored.meta.getContentType() + ";base64," + b64;
    }

    @Override
    public Optional<MediaObject> findById(String mediaObjectId) {
        if (!StringUtils.hasText(mediaObjectId)) {
            return Optional.empty();
        }
        Stored stored = byId.get(mediaObjectId.trim());
        return stored == null ? Optional.<MediaObject>empty() : Optional.of(stored.meta);
    }

    @Override
    public void delete(String mediaObjectId) {
        if (!StringUtils.hasText(mediaObjectId)) {
            return;
        }
        byId.remove(mediaObjectId.trim());
    }

    private Stored require(String mediaObjectId) {
        if (!StringUtils.hasText(mediaObjectId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "mediaObjectId 不能为空");
        }
        Stored stored = byId.get(mediaObjectId.trim());
        if (stored == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "媒体不存在");
        }
        return stored;
    }

    private static final class Stored {
        private final MediaObject meta;
        private final byte[] bytes;

        private Stored(MediaObject meta, byte[] bytes) {
            this.meta = meta;
            this.bytes = bytes;
        }
    }
}

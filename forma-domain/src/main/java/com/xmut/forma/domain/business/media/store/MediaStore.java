package com.xmut.forma.domain.business.media.store;

import com.xmut.forma.domain.business.media.model.MediaObject;

import java.util.Optional;

/**
 * 对象存储端口：业务只拿 {@code mediaObjectId}；可读 URL 由本端口签发。
 */
public interface MediaStore {

    /**
     * 写入字节并持久化元数据，返回业务 mediaObjectId。
     */
    MediaObject put(String userId, String contentType, byte[] bytes);

    /**
     * 签发短期可读 URL（或 data URI），供 Computer 显示。
     */
    String issueReadUrl(String mediaObjectId);

    Optional<MediaObject> findById(String mediaObjectId);

    /**
     * Best-effort 删除对象与元数据（补偿 put 后后续步骤失败留下的孤儿）。
     */
    void delete(String mediaObjectId);
}

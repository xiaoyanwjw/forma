package com.xmut.forma.domain.business.media.repository;

import com.xmut.forma.domain.business.media.model.MediaObject;

import java.util.Optional;

/**
 * 媒体元数据仓储（不含图片大字段）。
 */
public interface MediaObjectRepository {

    void save(MediaObject mediaObject);

    Optional<MediaObject> findById(String id);

    void deleteById(String id);
}

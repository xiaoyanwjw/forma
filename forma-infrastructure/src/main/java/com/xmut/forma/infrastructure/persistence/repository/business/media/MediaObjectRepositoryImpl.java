package com.xmut.forma.infrastructure.persistence.repository.business.media;

import com.xmut.forma.domain.business.media.model.MediaObject;
import com.xmut.forma.domain.business.media.repository.MediaObjectRepository;
import com.xmut.forma.infrastructure.persistence.mybatis.mapper.MediaObjectMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.MediaObjectPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MediaObjectRepositoryImpl implements MediaObjectRepository {

    private final MediaObjectMapper mediaObjectMapper;

    @Override
    public void save(MediaObject mediaObject) {
        mediaObjectMapper.insert(toPo(mediaObject));
    }

    @Override
    public Optional<MediaObject> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        MediaObjectPO po = mediaObjectMapper.selectByBizId(id.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public void deleteById(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        mediaObjectMapper.deleteByBizId(id.trim());
    }

    private static MediaObject toDomain(MediaObjectPO po) {
        MediaObject media = new MediaObject();
        media.setId(po.getBizId());
        media.setUserId(po.getUserId());
        media.setObjectKey(po.getObjectKey());
        media.setContentType(po.getContentType());
        media.setSizeBytes(po.getSizeBytes() == null ? 0L : po.getSizeBytes());
        media.setCreatedAt(po.getCreatedAt());
        return media;
    }

    private static MediaObjectPO toPo(MediaObject media) {
        MediaObjectPO po = new MediaObjectPO();
        po.setBizId(media.getId());
        po.setUserId(media.getUserId());
        po.setObjectKey(media.getObjectKey());
        po.setContentType(media.getContentType());
        po.setSizeBytes(media.getSizeBytes());
        po.setCreatedAt(media.getCreatedAt());
        return po;
    }
}

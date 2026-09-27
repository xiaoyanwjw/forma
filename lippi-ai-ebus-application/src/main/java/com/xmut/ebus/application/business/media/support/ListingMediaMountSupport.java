package com.xmut.ebus.application.business.media.support;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.logging.LoggerUtils;
import com.xmut.ebus.common.logging.NameValue;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Listing 成功路径：系统挂载占位主图（非用户上传、非模型生图），并写回 view + artifact。
 */
@Slf4j
@Component
public class ListingMediaMountSupport {

    public static final String MSG_MEDIA_BUSY = "服务繁忙，请稍后重试";

    /** 1×1 PNG */
    private static final byte[] PLACEHOLDER_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01,
            0x00, 0x05, (byte) 0xFE, (byte) 0xD4, (byte) 0xEF, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    private final MediaStore mediaStore;

    public ListingMediaMountSupport(MediaStore mediaStore) {
        this.mediaStore = mediaStore;
    }

    /**
     * @return view / businessPayload with real mediaObjectId + readable src
     */
    public MountedListingMedia mountSystemPlaceholder(String userId,
                                                      Map<String, Object> projectedView,
                                                      Map<String, Object> businessPayload) {
        MediaObject media;
        try {
            media = mediaStore.put(userId, "image/png", PLACEHOLDER_PNG);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            LoggerUtils.error(log, ListingMediaMountSupport.class, "mountSystemPlaceholder",
                    "mediaStore.put unexpected failure",
                    ex,
                    NameValue.create("userId", userId),
                    NameValue.create("step", "put"));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }
        if (media == null || !StringUtils.hasText(media.getId())) {
            LoggerUtils.error(log, ListingMediaMountSupport.class, "mountSystemPlaceholder",
                    "mediaStore.put returned blank mediaObjectId",
                    NameValue.create("userId", userId),
                    NameValue.create("step", "put"));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }

        final String readUrl;
        try {
            readUrl = mediaStore.issueReadUrl(media.getId());
        } catch (RuntimeException ex) {
            deleteQuietly(media.getId(), "issueReadUrl failed after put");
            if (ex instanceof BusinessException) {
                throw (BusinessException) ex;
            }
            LoggerUtils.error(log, ListingMediaMountSupport.class, "mountSystemPlaceholder",
                    "mediaStore.issueReadUrl unexpected failure",
                    ex,
                    NameValue.create("userId", userId),
                    NameValue.create("mediaObjectId", media.getId()),
                    NameValue.create("step", "issueReadUrl"));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }
        if (!StringUtils.hasText(readUrl)) {
            deleteQuietly(media.getId(), "blank readUrl after put");
            LoggerUtils.error(log, ListingMediaMountSupport.class, "mountSystemPlaceholder",
                    "issueReadUrl returned blank url",
                    NameValue.create("userId", userId),
                    NameValue.create("mediaObjectId", media.getId()),
                    NameValue.create("step", "issueReadUrl"));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, MSG_MEDIA_BUSY);
        }

        Map<String, Object> payload = businessPayload != null
                ? new LinkedHashMap<String, Object>(businessPayload)
                : new LinkedHashMap<String, Object>();
        payload.put("mediaObjectIds", Collections.singletonList(media.getId()));

        Map<String, Object> view = projectedView != null
                ? deepCopyMap(projectedView)
                : new LinkedHashMap<String, Object>();
        injectHeroMedia(view, media.getId(), readUrl, payload);

        return new MountedListingMedia(view, payload, media.getId());
    }

    private void deleteQuietly(String mediaObjectId, String reason) {
        try {
            mediaStore.delete(mediaObjectId);
        } catch (RuntimeException cleanupEx) {
            LoggerUtils.warn(log, ListingMediaMountSupport.class, "deleteQuietly",
                    cleanupEx.getMessage() != null ? cleanupEx.getMessage() : "orphan cleanup failed",
                    NameValue.create("mediaObjectId", mediaObjectId),
                    NameValue.create("reason", reason));
        }
    }

    @SuppressWarnings("unchecked")
    static void injectHeroMedia(Map<String, Object> view,
                                String mediaObjectId,
                                String readUrl,
                                Map<String, Object> payload) {
        Object blocksObj = view.get("blocks");
        List<Object> blocks;
        if (blocksObj instanceof List) {
            blocks = new ArrayList<Object>((List<Object>) blocksObj);
        } else {
            blocks = new ArrayList<Object>();
        }

        int heroIndex = -1;
        int firstMediaIndex = -1;
        for (int i = 0; i < blocks.size(); i++) {
            Object blockObj = blocks.get(i);
            if (!(blockObj instanceof Map)) {
                continue;
            }
            Map<?, ?> block = (Map<?, ?>) blockObj;
            if (!"media".equals(block.get("type"))) {
                continue;
            }
            if (firstMediaIndex < 0) {
                firstMediaIndex = i;
            }
            if ("hero".equals(block.get("role"))) {
                heroIndex = i;
                break;
            }
        }

        int target = heroIndex >= 0 ? heroIndex : firstMediaIndex;
        if (target >= 0) {
            Map<String, Object> block = new LinkedHashMap<String, Object>(
                    (Map<String, Object>) blocks.get(target));
            patchMediaBlock(block, mediaObjectId, readUrl, payload);
            blocks.set(target, block);
        } else {
            Map<String, Object> media = new LinkedHashMap<String, Object>();
            media.put("type", "media");
            media.put("role", "hero");
            patchMediaBlock(media, mediaObjectId, readUrl, payload);
            media.put("alt", "主图");
            blocks.add(0, media);
        }
        view.put("blocks", blocks);
    }

    private static void patchMediaBlock(Map<String, Object> block,
                                        String mediaObjectId,
                                        String readUrl,
                                        Map<String, Object> payload) {
        block.put("mediaObjectId", mediaObjectId);
        block.put("src", readUrl);
        if (!StringUtils.hasText(stringVal(block.get("placeholder")))) {
            Object heroPlan = payload.get("heroPlan");
            if (heroPlan != null && StringUtils.hasText(String.valueOf(heroPlan))) {
                block.put("placeholder", String.valueOf(heroPlan).trim());
            } else {
                block.put("placeholder", "主图方案");
            }
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                copy.put(e.getKey(), deepCopyMap((Map<String, Object>) v));
            } else if (v instanceof List) {
                copy.put(e.getKey(), deepCopyList((List<?>) v));
            } else {
                copy.put(e.getKey(), v);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<Object>();
        for (Object v : source) {
            if (v instanceof Map) {
                copy.add(deepCopyMap((Map<String, Object>) v));
            } else if (v instanceof List) {
                copy.add(deepCopyList((List<?>) v));
            } else {
                copy.add(v);
            }
        }
        return copy;
    }

    public static final class MountedListingMedia {
        private final Map<String, Object> view;
        private final Map<String, Object> businessPayload;
        private final String mediaObjectId;

        public MountedListingMedia(Map<String, Object> view,
                                   Map<String, Object> businessPayload,
                                   String mediaObjectId) {
            this.view = view;
            this.businessPayload = businessPayload;
            this.mediaObjectId = mediaObjectId;
        }

        public Map<String, Object> getView() {
            return view;
        }

        public Map<String, Object> getBusinessPayload() {
            return businessPayload;
        }

        public String getMediaObjectId() {
            return mediaObjectId;
        }
    }
}

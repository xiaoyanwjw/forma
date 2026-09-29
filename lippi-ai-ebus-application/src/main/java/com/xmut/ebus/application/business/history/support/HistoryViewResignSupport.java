package com.xmut.ebus.application.business.history.support;

import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.media.model.MediaObject;
import com.xmut.ebus.domain.business.media.store.MediaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 历史详情：对 payload.view 中 media 块按 mediaObjectId 重签可读 URL（非第二真相）。
 */
@Component
@RequiredArgsConstructor
public class HistoryViewResignSupport {

    private final MediaStore mediaStore;

    @SuppressWarnings("unchecked")
    public Map<String, Object> resignView(Map<String, Object> view, String ownerUserId) {
        if (view == null || view.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = deepCopyMap(view);
        Object blocksObj = copy.get("blocks");
        if (!(blocksObj instanceof List)) {
            return copy;
        }
        List<Object> blocks = (List<Object>) blocksObj;
        for (int i = 0; i < blocks.size(); i++) {
            Object blockObj = blocks.get(i);
            if (!(blockObj instanceof Map)) {
                continue;
            }
            Map<String, Object> block = (Map<String, Object>) blockObj;
            if (!"media".equals(block.get("type"))) {
                continue;
            }
            String mediaObjectId = stringVal(block.get("mediaObjectId"));
            if (!StringUtils.hasText(mediaObjectId)) {
                continue;
            }
            Optional<MediaObject> media = mediaStore.findById(mediaObjectId);
            if (!media.isPresent()
                    || !StringUtils.hasText(ownerUserId)
                    || !ownerUserId.equals(media.get().getUserId())) {
                // 缺元数据或非本人：保留旧 src，不签发
                continue;
            }
            try {
                String url = mediaStore.issueReadUrl(mediaObjectId);
                if (StringUtils.hasText(url)) {
                    block.put("src", url);
                }
            } catch (RuntimeException ignored) {
                // 保留旧 src / placeholder；不因单图签发失败整页失败
            }
            blocks.set(i, block);
        }
        copy.put("blocks", blocks);
        return copy;
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
}

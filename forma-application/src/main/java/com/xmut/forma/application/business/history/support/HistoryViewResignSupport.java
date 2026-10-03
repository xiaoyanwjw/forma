package com.xmut.forma.application.business.history.support;

import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.media.model.MediaObject;
import com.xmut.forma.domain.business.media.store.MediaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史详情：对 payload.view 中 media 块按 mediaObjectId 重签可读 URL（非第二真相）。
 */
@Component
@RequiredArgsConstructor
public class HistoryViewResignSupport {

    private static final Pattern V2_MEDIA_IMG = Pattern.compile(
            "<img[^>]*data-(?:forma|adam)-media-object-id=\"([^\"]+)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private final MediaStore mediaStore;

    @SuppressWarnings("unchecked")
    public Map<String, Object> resignView(Map<String, Object> view, String ownerUserId) {
        if (view == null || view.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = deepCopyMap(view);
        Object version = copy.get("version");
        if (version instanceof Number && ((Number) version).intValue() == 2) {
            Object contentObj = copy.get("content");
            if (contentObj instanceof String) {
                copy.put("content", resignV2HtmlContent((String) contentObj, ownerUserId));
            }
        }
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

    private String resignV2HtmlContent(String content, String ownerUserId) {
        Matcher matcher = V2_MEDIA_IMG.matcher(content);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String fullImg = matcher.group(0);
            String mediaObjectId = matcher.group(1);
            String freshUrl = issueReadUrlIfOwned(mediaObjectId, ownerUserId);
            if (!StringUtils.hasText(freshUrl)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(fullImg));
                continue;
            }
            String patched = fullImg.replaceFirst("src=\"[^\"]*\"", "src=\"" + freshUrl + "\"");
            matcher.appendReplacement(out, Matcher.quoteReplacement(patched));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String issueReadUrlIfOwned(String mediaObjectId, String ownerUserId) {
        Optional<MediaObject> media = mediaStore.findById(mediaObjectId);
        if (!media.isPresent()
                || !StringUtils.hasText(ownerUserId)
                || !ownerUserId.equals(media.get().getUserId())) {
            return null;
        }
        try {
            String url = mediaStore.issueReadUrl(mediaObjectId);
            return StringUtils.hasText(url) ? url : null;
        } catch (RuntimeException ignored) {
            return null;
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
}

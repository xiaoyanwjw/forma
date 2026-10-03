package com.xmut.forma.pi.ai.message;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * Hermes 多模态内容块（Story 51-5）。
 *
 * <p>本模块自有类型。
 */
@Value
@Builder
public class ContentPart {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE_URL = "image_url";

    /** {@link #TYPE_TEXT} 或 {@link #TYPE_IMAGE_URL}。 */
    String type;

    /** text 块正文。 */
    String text;

    /** image_url 块 URL（http(s) 或 data URL）。 */
    String url;

    /** image_url 细节：low / high / auto；可空。 */
    String detail;

    public static ContentPart text(String text) {
        return ContentPart.builder()
                .type(TYPE_TEXT)
                .text(text != null ? text : "")
                .build();
    }

    public static ContentPart imageUrl(String url) {
        return imageUrl(url, "auto");
    }

    public static ContentPart imageUrl(String url, String detail) {
        return ContentPart.builder()
                .type(TYPE_IMAGE_URL)
                .url(url)
                .detail(StringUtils.hasText(detail) ? detail : "auto")
                .build();
    }

    public boolean isText() {
        return TYPE_TEXT.equalsIgnoreCase(type);
    }

    public boolean isImageUrl() {
        return TYPE_IMAGE_URL.equalsIgnoreCase(type);
    }
}

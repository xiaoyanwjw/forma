package com.xmut.ebus.common.util;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;

/**
 * 字符串工具与守卫。业务侧不要手写 {@code null || trim().isEmpty()}。
 */
public final class StringUtils {

    private StringUtils() {
    }

    /** 非 null 且 trim 后非空。 */
    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** {@link #hasText} 的反义。 */
    public static boolean isBlank(String value) {
        return !hasText(value);
    }

    /**
     * 非空白字符串；返回 trim 后的值。失败码 {@link ErrorCode#PARAM_INVALID}。
     */
    public static String requireHasText(String value, String message) {
        return requireHasText(value, ErrorCode.PARAM_INVALID, message);
    }

    /**
     * 非空白字符串；失败时抛指定 {@link ErrorCode}（消息用错误码默认文案）。
     */
    public static String requireHasText(String value, ErrorCode errorCode) {
        return requireHasText(value, errorCode, errorCode.getMessage());
    }

    /**
     * 非空白字符串；失败时抛指定 {@link ErrorCode} + 自定义消息。
     */
    public static String requireHasText(String value, ErrorCode errorCode, String message) {
        if (!hasText(value)) {
            throw new BusinessException(errorCode, message);
        }
        return value.trim();
    }
}

package com.xmut.forma.common.util;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;

import java.util.Collection;
import java.util.Map;

/**
 * 对象 / 集合守卫。缺参抛 {@link BusinessException}（默认 {@link ErrorCode#PARAM_INVALID}）。
 * <p>
 * 字符串空白请用 {@link StringUtils}；域内规则仍留在 ApplicationService 的 {@code require*}。
 */
public final class ObjectUtils {

    private ObjectUtils() {
    }

    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value;
    }

    public static <T extends Collection<?>> T requireNotEmpty(T value, String message) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value;
    }

    public static <T extends Map<?, ?>> T requireNotEmpty(T value, String message) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value;
    }
}

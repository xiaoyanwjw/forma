package com.xmut.ebus.common.util;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringUtilsTest {

    @Test
    void hasTextAndIsBlank() {
        assertEquals(true, StringUtils.hasText(" a "));
        assertEquals(false, StringUtils.hasText("  "));
        assertEquals(true, StringUtils.isBlank(null));
    }

    @Test
    void requireHasTextTrims() {
        assertEquals("ab", StringUtils.requireHasText("  ab  ", "不能为空"));
    }

    @Test
    void requireHasTextRejectsBlank() {
        assertThrows(BusinessException.class, () -> StringUtils.requireHasText("  ", "不能为空"));
    }

    @Test
    void requireHasTextWithCustomErrorCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StringUtils.requireHasText("  ", ErrorCode.CREDIT_HOLD_INVALID));
        assertEquals(ErrorCode.CREDIT_HOLD_INVALID, ex.getErrorCode());
    }
}

class ObjectUtilsTest {

    @Test
    void requireNonNullRejectsNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ObjectUtils.requireNonNull(null, "对象不能为空"));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void requireNotEmptyCollection() {
        List<String> list = Arrays.asList("a");
        assertSame(list, ObjectUtils.requireNotEmpty(list, "列表不能空"));
        assertThrows(BusinessException.class,
                () -> ObjectUtils.requireNotEmpty(Collections.emptyList(), "列表不能空"));
    }

    @Test
    void requireNotEmptyMap() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("k", "v");
        assertSame(map, ObjectUtils.requireNotEmpty(map, "map 不能空"));
        assertThrows(BusinessException.class,
                () -> ObjectUtils.requireNotEmpty(Collections.emptyMap(), "map 不能空"));
    }
}

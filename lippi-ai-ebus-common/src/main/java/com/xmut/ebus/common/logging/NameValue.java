package com.xmut.ebus.common.logging;

/**
 * 结构化日志键值对（裁剪自 LIMS）。
 */
public final class NameValue<T> {

    private final String name;
    private final T value;

    private NameValue(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public static <T> NameValue<T> create(String name, T value) {
        return new NameValue<T>(name, value);
    }

    @Override
    public String toString() {
        return String.format("%s=%s", name, value);
    }
}

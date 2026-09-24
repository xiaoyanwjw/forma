package com.xmut.ebus.common.util;

import java.util.UUID;

/**
 * 生成请求链路 traceId（无租户语义）。
 */
public final class TraceUtils {

    private TraceUtils() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

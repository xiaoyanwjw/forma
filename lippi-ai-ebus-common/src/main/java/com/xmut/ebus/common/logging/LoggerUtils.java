package com.xmut.ebus.common.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * 统一监控埋点 / 业务失败日志（裁剪自 LIMS {@code LoggerUtils}）。
 * <p>
 * 格式前缀：{@code [Ebus_Monitor_V1] result=true|false, category=类名_方法名}；
 * 附带 {@code traceId}（MDC）与 {@link NameValue} 键值对。
 * <ul>
 *   <li>{@link #success} / {@link #error} — 流程关键节点成对埋点（算成功率）</li>
 *   <li>{@link #warn} — 可恢复或业务拒绝类关键路径</li>
 * </ul>
 * 禁止写入密码、JWT、密钥。
 */
public final class LoggerUtils {

    private static final String MONITOR_VERSION = "Ebus_Monitor_V1";
    private static final String TRACE_ID_KEY = "traceId";

    private LoggerUtils() {
    }

    public static void success(Logger logger, Class<?> cls, String method, NameValue<?>... args) {
        success(logger, categoryOf(cls, method), args);
    }

    public static void success(Logger logger, String category, NameValue<?>... args) {
        String prefix = buildPrefix(category, true);
        logger.info("{} traceId={}, {}", prefix, getTraceId(), buildArgs(args));
    }

    public static void error(Logger logger, Class<?> cls, String method, String reason, NameValue<?>... args) {
        error(logger, categoryOf(cls, method), reason, args);
    }

    public static void error(Logger logger, String category, String reason, NameValue<?>... args) {
        logger.error(buildErrorLog(category, reason, null, args));
    }

    public static void error(Logger logger, Class<?> cls, String method, String reason, Throwable e,
                             NameValue<?>... args) {
        error(logger, categoryOf(cls, method), reason, e, args);
    }

    public static void error(Logger logger, String category, String reason, Throwable e, NameValue<?>... args) {
        String errLog = buildErrorLog(category, reason, e != null ? e.getMessage() : null, args);
        logger.error("{}, ", errLog, e);
    }

    public static void warn(Logger logger, Class<?> cls, String method, String reason, NameValue<?>... args) {
        warn(logger, categoryOf(cls, method), reason, args);
    }

    public static void warn(Logger logger, String category, String reason, NameValue<?>... args) {
        logger.warn(buildErrorLog(category, reason, null, args));
    }

    private static String categoryOf(Class<?> cls, String method) {
        return getClassNameSafe(cls) + "_" + method;
    }

    private static String buildPrefix(String category, boolean result) {
        return String.format("[%s] result=%s, category=%s,", MONITOR_VERSION, result, category);
    }

    private static String buildArgs(NameValue<?>... args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (NameValue<?> arg : args) {
            builder.append(arg.toString()).append(", ");
        }
        return builder.toString();
    }

    private static String buildErrorLog(String category, String reason, String description, NameValue<?>... args) {
        return String.format("%s reason=%s, traceId=%s, %sdescription=%s",
                buildPrefix(category, false),
                reason,
                getTraceId(),
                buildArgs(args),
                description == null ? "" : description.trim());
    }

    private static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : "";
    }

    private static String getClassNameSafe(Class<?> cls) {
        return cls == null ? "null" : cls.getSimpleName();
    }
}

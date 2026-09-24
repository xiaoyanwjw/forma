package com.xmut.ebus.common.exception;

/**
 * 业务错误码（最小集：未授权 / 冲突 / 限流 / 鉴权失败）。
 */
public enum ErrorCode {

    PARAM_INVALID("PARAM_INVALID", "参数无效", 400),
    UNAUTHORIZED("UNAUTHORIZED", "未授权，请先登录", 401),
    BAD_CREDENTIALS("BAD_CREDENTIALS", "用户名或密码错误", 401),
    CONFLICT("CONFLICT", "用户名或邮箱已被占用", 409),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁，请稍后再试", 429),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统错误", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

package com.xmut.forma.pi.agent.tool;

/**
 * 工具校验/装载异常。
 * 功能描述：在 Manifest 非法或扫描失败时抛出。
 */
public class ToolValidationException extends RuntimeException {

    public ToolValidationException(String message) {
        super(message);
    }

    public ToolValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

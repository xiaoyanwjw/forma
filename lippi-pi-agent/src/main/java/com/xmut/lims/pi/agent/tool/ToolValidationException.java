package com.xmut.lims.pi.agent.tool;

/**
 * ToolManifest 校验失败或扫描装载失败。
 */
public class ToolValidationException extends RuntimeException {

    public ToolValidationException(String message) {
        super(message);
    }

    public ToolValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

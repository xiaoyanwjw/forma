package com.xmut.forma.pi.ai.exception;

/** 网络 / 非 200 HTTP / 响应解析失败。 */
public class PiModelIoException extends PiModelException {

    public PiModelIoException(String message) {
        super(message);
    }

    public PiModelIoException(String message, Throwable cause) {
        super(message, cause);
    }

    public PiModelIoException(String message, String provider, String model, Throwable cause) {
        super(message, provider, model, cause);
    }
}

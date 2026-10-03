package com.xmut.forma.pi.ai.exception;

/**
 * 厂商模型调用失败基类。不继承业务 {@code BusinessException}。
 */
public class PiModelException extends RuntimeException {

    private final String provider;
    private final String model;

    public PiModelException(String message) {
        this(message, null, null, null);
    }

    public PiModelException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public PiModelException(String message, String provider, String model, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }
}

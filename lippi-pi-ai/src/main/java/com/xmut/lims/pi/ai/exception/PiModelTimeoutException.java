package com.xmut.lims.pi.ai.exception;

/** 超时（SocketTimeout 或 HTTP 408）。 */
public class PiModelTimeoutException extends PiModelException {

    public PiModelTimeoutException(String message, String provider, String model, Throwable cause) {
        super(message, provider, model, cause);
    }
}

package com.xmut.forma.pi.ai.exception;

/** HTTP 429。 */
public class PiModelRateLimitException extends PiModelException {

    public PiModelRateLimitException(String message, String provider, String model) {
        super(message, provider, model, null);
    }
}

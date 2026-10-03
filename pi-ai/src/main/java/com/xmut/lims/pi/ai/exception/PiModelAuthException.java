package com.xmut.lims.pi.ai.exception;

/** HTTP 401 / 缺 Key。 */
public class PiModelAuthException extends PiModelException {

    public PiModelAuthException(String message, String provider, String model) {
        super(message, provider, model, null);
    }
}

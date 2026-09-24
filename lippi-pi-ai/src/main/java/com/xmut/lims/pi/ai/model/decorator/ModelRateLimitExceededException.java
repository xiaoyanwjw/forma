package com.xmut.lims.pi.ai.model.decorator;

/**
 * 模型调用超额。
 */
public class ModelRateLimitExceededException extends RuntimeException {

    public ModelRateLimitExceededException(String message) {
        super(message);
    }
}

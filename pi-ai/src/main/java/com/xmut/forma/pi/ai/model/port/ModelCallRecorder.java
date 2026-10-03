package com.xmut.forma.pi.ai.model.port;

/**
 * 模型调用审计反向端口 <b>[Lippi]</b>。
 */
public interface ModelCallRecorder {

    void record(ModelCallRecord record);

    /** 无操作。 */
    ModelCallRecorder NOOP = record -> {
        // no-op
    };
}

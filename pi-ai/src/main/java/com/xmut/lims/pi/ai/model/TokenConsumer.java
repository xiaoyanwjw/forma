package com.xmut.lims.pi.ai.model;

/**
 * 模型层流式 token 接收口。不依赖 session 包；由节点翻译为 {@code MESSAGE_UPDATE}。
 */
public interface TokenConsumer {

    void onTextDelta(String delta);

    /** 含 toolCalls；供节点写 state。 */
    void onComplete(ModelResponse response);
}

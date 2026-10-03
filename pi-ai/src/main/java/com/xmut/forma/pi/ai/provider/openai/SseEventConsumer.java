package com.xmut.forma.pi.ai.provider.openai;

/**
 * OpenAI 兼容 SSE：每个 {@code data:} JSON 对象一次；{@code [DONE]} 后 {@link #onDone()}。
 */
interface SseEventConsumer {

    void onNext(String jsonObject);

    void onDone();
}

package com.xmut.lims.pi.ai.model;

/**
 * L2 模型调用端口 <b>[Lippi]</b>。
 *
 * <p>类型名<strong>不是</strong>上游 Hermes 原语；调用语义对齐上游一次
 * {@code chat.completions.create(..., tools=?)}。
 *
 * <p>公共 API：{@code complete} 与 {@code stream}。{@code request.tools} 可空（null/empty → 不带 tools）。
 * <strong>禁止</strong>再暴露 {@code chat} + {@code chatWithTools} 双方法；
 * <strong>禁止</strong>照搬 agent {@code Capability} / {@code ChatCompletion}。
 *
 * <p>{@code stream} 默认 fallback 一次 {@code complete} → 单个 text delta（Stub / 未覆盖时）。
 * 厂商客户端应覆盖为真 SSE。能力描述见 {@link ModelModality}。
 */
public interface ModelProvider {

    /**
     * 单次补全：返回文本与可选结构化 {@code toolCalls}（永不 null）。
     *
     * @param request 消息、可选 tools、useCase / session / trace 等
     * @return 模型响应；{@code toolCalls} 列表永不 null
     */
    ModelResponse complete(ModelRequest request);

    /**
     * 流式补全。默认：一次 {@link #complete}，有文本则推单个 {@code onTextDelta}，再 {@code onComplete}。
     *
     * <p>模型层使用 {@link TokenConsumer}，不依赖 session 包。
     */
    default void stream(ModelRequest request, TokenConsumer consumer) {
        ModelResponse r = ModelResponse.norm(complete(request));
        if (r.getContent() != null && !r.getContent().isEmpty()) {
            consumer.onTextDelta(r.getContent());
        }
        consumer.onComplete(r);
    }
}

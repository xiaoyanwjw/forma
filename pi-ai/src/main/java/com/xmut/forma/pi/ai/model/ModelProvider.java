package com.xmut.forma.pi.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * L2 模型调用端口 <b>[Lippi]</b>。
 *
 * <p>类型名<strong>不是</strong>上游 Hermes 原语；调用语义对齐上游一次
 * {@code chat.completions.create(..., tools=?)}。
 *
 * <p>公共 API：{@code complete}、{@code completeBatch} 与 {@code stream}。{@code request.tools} 可空（null/empty → 不带 tools）。
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

    /** 默认批处理并发上限；限流仍按条 {@link #complete} 生效。 */
    int DEFAULT_BATCH_PARALLELISM = 8;

    /**
     * 一批补全，结果下标与 {@code requests} 对齐。
     *
     * <p>默认用共享线程池并行调用 {@link #complete}（限流/重试装饰器仍按条生效）。
     * 用于旁路任务（如科技速读按块摘句）：每条独立窗口，不拼进主会话。
     * 厂商真 Batch API 可覆盖本方法；近端不要求 HTTP 侧合并成一次请求。
     *
     * <p>空/null 返回空列表。某一条 {@code complete} 抛错则取消其余任务并抛出该错误。
     */
    default List<ModelResponse> completeBatch(List<ModelRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        int n = requests.size();
        if (n == 1) {
            return Collections.singletonList(complete(requests.get(0)));
        }
        List<Future<ModelResponse>> futures = new ArrayList<Future<ModelResponse>>(n);
        for (int i = 0; i < n; i++) {
            final ModelRequest req = requests.get(i);
            futures.add(ModelCompleteBatchExecutor.POOL.submit(new Callable<ModelResponse>() {
                @Override
                public ModelResponse call() {
                    return complete(req);
                }
            }));
        }
        List<ModelResponse> out = new ArrayList<ModelResponse>(n);
        for (int i = 0; i < n; i++) {
            try {
                out.add(futures.get(i).get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
                throw new IllegalStateException("completeBatch interrupted", e);
            } catch (ExecutionException e) {
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException(cause);
            }
        }
        return out;
    }

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

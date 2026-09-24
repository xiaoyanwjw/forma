package com.xmut.lims.pi.agent.session;

import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.event.PiEvent;

import java.util.function.Consumer;

/**
 * Pi Runtime 唯一对外产品门面（Story 51-12 / architecture §2.4）。
 *
 * <p>业务调用方只依赖本接口；禁止注入 Runtime {@code Agent} / {@code ConversationLoop}。
 */
public interface AgentSession {

    /**
     * 订阅生命周期事件（长驻）；对应 bus {@code observe}。
     *
     * @return 取消订阅；{@code handler} 不可为 null
     */
    AutoCloseable subscribe(Consumer<PiEvent> handler);

    /**
     * 发起一轮 / 一次任务（对齐开源 pi {@code prompt}）。
     *
     * <p>过程只经 bus {@code emit}（COMMAND / BEFORE_AGENT_START / AGENT_START … AGENT_END）；
     * 返回终态 {@link TurnResult}。无 {@code stream} 通路。
     */
    TurnResult prompt(PromptRequest request);

    /**
     * HITL / checkpoint 恢复（[LIMS]；语义同原 {@code Agent#resume}）。
     */
    TurnResult resume(ResumeRequest request);

    /**
     * 压缩会话上下文。
     *
     * <p>当前实现可为 NOOP（锚点 API 已就绪，CLI 接线后置）；调用方勿假设消息已被改写。
     */
    CompactResult compact(CompactRequest request);

    /**
     * 取消进行中的 run。
     */
    void cancel(String runId, String reason);
}

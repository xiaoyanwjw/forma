package com.xmut.forma.pi.agent.session;

import com.xmut.forma.pi.agent.ResumeRequest;
import com.xmut.forma.pi.agent.event.PiEvent;

import java.util.function.Consumer;

/**
 * Pi Runtime 对外门面（业务唯一入口）。
 * 功能描述：负责一轮对话的会话读写与终态交付（prompt / resume / compact / cancel / subscribe）。
 * 关键设计：Transcript 归 SessionStore；图挂起态归内部 Agent/Checkpointer；业务禁止直接注入 Agent。
 */
public interface AgentSession {

    /**
     * 订阅生命周期事件（长驻）；对应 bus {@code observe}。
     *
     * @return 取消订阅；{@code handler} 不可为 null
     */
    AutoCloseable subscribe(Consumer<PiEvent> handler);

    /**
     * 发起一轮对话（对齐开源 Pi {@code prompt}）。
     *
     * <p>过程经 bus 发出 COMMAND / BEFORE_AGENT_START / AGENT_START … AGENT_END；
     * 返回终态 {@link TurnResult}。无独立 stream 通路（流式增量走事件）。
     */
    TurnResult prompt(PromptRequest request);

    /**
     * HITL / 图 checkpoint 恢复（语义同内部 {@code Agent#resume}）。
     *
     * <p>≠ 上游 Session 的 {@code /resume} 命令。
     */
    TurnResult resume(ResumeRequest request);

    /**
     * 压缩会话上下文。
     *
     * <p>当前可为 NOOP（{@link SessionStore#setCompactAnchor} 已就绪，CLI 接线后置）；
     * 调用方勿假设消息已被改写。
     */
    CompactResult compact(CompactRequest request);

    /**
     * 取消进行中的 run。
     */
    void cancel(String runId, String reason);
}

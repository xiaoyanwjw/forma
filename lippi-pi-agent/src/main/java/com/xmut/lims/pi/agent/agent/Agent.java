package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.event.Emitter;

/**
 * Pi ConversationLoop：内部控制流 = 模块内 StateGraph 超步（invoke / resume）。
 *
 * <p>仅供 {@link com.xmut.lims.pi.agent.session.AgentSession} 内部委托；禁止作为业务门面注入。
 * <p>入参是已绑定的 {@link TurnInput}（完整 chat 轴），不是「半成品 Prompt」。
 */
public interface Agent {

    /** 执行一轮对话 / 任务（同步 invoke 默认 Tool-loop 图）。 */
    default ConversationResult run(TurnInput turn) {
        return run(turn, null);
    }

    /** 执行一轮对话；{@code emitter} 可为 null（无订阅）。 */
    ConversationResult run(TurnInput turn, Emitter emitter);

    /** 从 checkpoint / HITL 恢复。 */
    default ConversationResult resume(ResumeRequest request) {
        return resume(request, null);
    }

    /** 从 checkpoint / HITL 恢复；{@code emitter} 可为 null（无订阅）。 */
    ConversationResult resume(ResumeRequest request, Emitter emitter);

    /** 取消进行中的 run（下一超步边界生效）。空白 runId 静默 no-op。 */
    void cancel(String runId, String reason);
}

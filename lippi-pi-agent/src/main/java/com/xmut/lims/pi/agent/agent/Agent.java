package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.event.Emitter;

/**
 * 内部对话控制流（ConversationLoop）。
 * 功能描述：在一张 StateGraph 上执行 invoke / resume 超步。
 * 关键设计：仅供 AgentSession 委托；入参是已绑定的 TurnInput，不是半成品 Prompt。
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

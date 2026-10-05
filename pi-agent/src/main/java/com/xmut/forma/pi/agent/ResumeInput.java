package com.xmut.forma.pi.agent;

import com.xmut.forma.pi.agent.extension.ContextModifier;
import com.xmut.forma.pi.agent.tool.ToolDecision;
import lombok.Builder;
import lombok.Value;

/**
 * 已绑定的 HITL 续跑入参。
 * 功能描述：携带 run/session 标识与 tool-result / WRITE 决策。
 * 关键设计：由 AgentSession hydrate 后产出；Agent 不再读半成品 {@link ResumeRequest}。
 */
@Value
@Builder(toBuilder = true)
public class ResumeInput {

    String sessionId;

    String runId;

    /** {@code before_agent_start} 对三槽的 overwrite / append。 */
    ContextModifier contextModifier;

    /**
     * 人工输入：WRITE 路径作说明/拒绝原因；tool-result 路径作对应 {@link #toolCallId} 的结果正文。
     */
    String humanInput;

    /**
     * tool-result 续跑：挂起 {@code TOOL_CALLS} 中待回答的 call id。
     * 非空时走 tool-result 路径，与 {@link #decision}/{@link #approved} 互斥。
     */
    String toolCallId;

    ToolDecision decision;

    /**
     * 布尔简写：{@code true}=APPROVE，{@code false}=DENY；{@code null} 表示未指定。
     */
    Boolean approved;

    String confirmId;

    String traceId;

    ResumeInput(String sessionId,
                String runId,
                ContextModifier contextModifier,
                String humanInput,
                String toolCallId,
                ToolDecision decision,
                Boolean approved,
                String confirmId,
                String traceId) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.contextModifier = contextModifier;
        this.humanInput = humanInput;
        this.toolCallId = toolCallId;
        this.decision = decision;
        this.approved = approved;
        this.confirmId = confirmId;
        this.traceId = traceId;
    }
}

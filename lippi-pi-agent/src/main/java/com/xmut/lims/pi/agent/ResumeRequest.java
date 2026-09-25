package com.xmut.lims.pi.agent;

import com.xmut.lims.pi.agent.agent.DefaultAgent;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import lombok.Builder;
import lombok.Value;

/**
 * Agent.resume 入参。
 * 功能描述：用于图级 HITL / checkpoint 恢复。
 * 关键设计：≠ 上游 Session 的 /resume 斜杠命令。
 */
@Value
@Builder(toBuilder = true)
public class ResumeRequest {

    /** 进行中 / 挂起的 run 标识。 */
    String runId;

    /** 人工输入（HITL）；拒绝原因等说明，可进 {@code HUMAN_INPUT}。 */
    String humanInput;

    /** 会话 ID（可选；对齐 TurnInput）。 */
    String sessionId;

    /** 链路追踪 ID。 */
    String traceId;

    /**
     * WRITE HITL 决策：{@link ToolDecision#APPROVE} / {@link ToolDecision#DENY}。
     *
     * <p>也可用 {@link #approved} 布尔简写；二者同时出现时以 {@code decision} 为准。
     * <p>{@link DefaultAgent#resume} 要求二者至少其一
     * （fail-closed：缺决策直接 FAILED，禁止再次挂起）。
     */
    ToolDecision decision;

    /**
     * 布尔简写：{@code true}=APPROVE，{@code false}=DENY；{@code null} 表示未指定。
     */
    Boolean approved;

    /**
     * 确认请求 ID：与 {@code runId} 组成幂等键 {@code (runId, confirmRequestId)}。
     *
     * <p>非空时：首次 {@code resume} 原子占位（Redis {@code SET NX EX} / 内存等价）；
     * 重复调用返回已完成摘要，WRITE handler 不再执行。为空时走非幂等单次路径
     *（仍受同 run {@code activeRuns} 互斥保护）。生产 HITL 客户端<strong>应传</strong>本字段。
     */
    String confirmRequestId;
}

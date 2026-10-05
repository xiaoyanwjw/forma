package com.xmut.forma.pi.agent;

import com.xmut.forma.common.output.TurnAttachment;
import com.xmut.forma.pi.agent.agent.DefaultAgent;
import com.xmut.forma.pi.agent.tool.ToolDecision;
import lombok.Builder;
import lombok.Value;

/**
 * Agent.resume 入参。
 * 功能描述：用于图级 HITL / checkpoint 恢复。
 * 关键设计：≠ 上游 Session 的 /resume 斜杠命令。
 *
 * <p>双模式互斥（{@link DefaultAgent#resume}）：
 * <ul>
 *   <li><b>tool-result</b>：非空 {@link #toolCallId} + 结果正文（{@link #humanInput}）—
 *       写入 transcript 并摘掉挂起 call，不再执行该 handler</li>
 *   <li><b>WRITE</b>：{@link #decision} / {@link #approved} — 既有批准路径</li>
 * </ul>
 * 二者都缺 → fail-closed FAILED；同时出现 → fail-closed FAILED。
 */
@Value
@Builder(toBuilder = true)
public class ResumeRequest {

    /** 进行中 / 挂起的 run 标识。 */
    String runId;

    /**
     * 人工输入：WRITE 路径作说明/拒绝原因；tool-result 路径作对应 {@link #toolCallId} 的结果正文。
     */
    String humanInput;

    /**
     * tool-result 续跑：挂起 {@code TOOL_CALLS} 中待回答的 call id（ask_human 等）。
     * 非空时走 tool-result 路径，与 {@link #decision}/{@link #approved} 互斥。
     */
    String toolCallId;

    /** 会话 ID（可选；对齐 TurnInput）。 */
    String sessionId;

    /** 链路追踪 ID。 */
    String traceId;

    /**
     * WRITE HITL 决策：{@link ToolDecision#APPROVE} / {@link ToolDecision#DENY}。
     *
     * <p>也可用 {@link #approved} 布尔简写；二者同时出现时以 {@code decision} 为准。
     * <p>与 {@link #toolCallId} 互斥；二者都缺则 {@link DefaultAgent#resume} fail-closed FAILED。
     */
    ToolDecision decision;

    /**
     * 布尔简写：{@code true}=APPROVE，{@code false}=DENY；{@code null} 表示未指定。
     */
    Boolean approved;

    /**
     * 确认请求 ID：与 {@code runId} 组成幂等键 {@code (runId, confirmId)}。
     *
     * <p>非空时：首次 {@code resume} 原子占位（Redis {@code SET NX EX} / 内存等价）；
     * 重复调用返回已完成摘要，WRITE handler 不再执行。为空时走非幂等单次路径
     *（仍受同 run {@code activeRuns} 互斥保护）。生产 HITL 客户端<strong>应传</strong>本字段。
     */
    String confirmId;

    /**
     * 本 run 工作区绝对路径；可空。
     * 有值时写入 {@code StateKeys.WORKSPACE_ROOT}，覆盖 checkpoint 中缺失或过期的根。
     */
    String workspaceRoot;

    /** 本轮 Skill id；resume 应从 run 带上，供 BEFORE_AGENT_START 查目录。 */
    String skillId;

    /** HITL 选项 id（如 confirm_execute）；非 HITL 可空。 */
    String resumeOptionId;

    /** 本轮交付附件；闲聊可空（null 视为 empty）。 */
    @Builder.Default
    TurnAttachment attachment = TurnAttachment.empty();

    ResumeRequest(String runId,
                  String humanInput,
                  String toolCallId,
                  String sessionId,
                  String traceId,
                  ToolDecision decision,
                  Boolean approved,
                  String confirmId,
                  String workspaceRoot,
                  String skillId,
                  String resumeOptionId,
                  TurnAttachment attachment) {
        this.runId = runId;
        this.humanInput = humanInput;
        this.toolCallId = toolCallId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.decision = decision;
        this.approved = approved;
        this.confirmId = confirmId;
        this.workspaceRoot = workspaceRoot;
        this.skillId = skillId;
        this.resumeOptionId = resumeOptionId;
        this.attachment = attachment == null ? TurnAttachment.empty() : attachment;
    }
}

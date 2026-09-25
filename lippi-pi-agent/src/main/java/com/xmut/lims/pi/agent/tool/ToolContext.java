package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.NodeContext;
import com.xmut.lims.pi.agent.graph.StateKeys;
import org.springframework.util.StringUtils;

/**
 * 工具执行上下文。
 * 功能描述：携带 runId / traceId / activeSkillId。
 * 关键设计：不携带 tenant / user。
 */
public final class ToolContext {

    private final String runId;
    private final String traceId;
    private final String activeSkillId;

    public ToolContext(String runId, String traceId) {
        this(runId, traceId, null);
    }

    public ToolContext(String runId, String traceId, String activeSkillId) {
        this.runId = runId;
        this.traceId = traceId;
        this.activeSkillId = StringUtils.hasText(activeSkillId) ? activeSkillId.trim() : null;
    }

    public static ToolContext from(NodeContext nodeContext) {
        return from(nodeContext, null);
    }

    public static ToolContext from(NodeContext nodeContext, GraphState state) {
        String active = null;
        if (state != null) {
            Object raw = state.get(StateKeys.ACTIVE_SKILL_ID);
            if (raw instanceof String && StringUtils.hasText((String) raw)) {
                active = ((String) raw).trim();
            }
        }
        if (nodeContext == null) {
            return new ToolContext(null, null, active);
        }
        return new ToolContext(
                nodeContext.getRunId(),
                nodeContext.getTraceId(),
                active);
    }

    public String getRunId() {
        return runId;
    }

    public String getTraceId() {
        return traceId;
    }

    /** 本轮 ActiveSkill id；可空（无 Active 时不限制 read_skill 目标）。 */
    public String getActiveSkillId() {
        return activeSkillId;
    }
}

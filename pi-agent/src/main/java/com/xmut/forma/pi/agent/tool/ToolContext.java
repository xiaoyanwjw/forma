package com.xmut.forma.pi.agent.tool;

import com.xmut.forma.pi.agent.graph.GraphState;
import com.xmut.forma.pi.agent.graph.NodeContext;
import com.xmut.forma.pi.agent.graph.StateKeys;
import org.springframework.util.StringUtils;

/**
 * 工具执行上下文。
 * 功能描述：携带 runId / traceId / activeSkillId / workspaceRoot。
 * 关键设计：不携带 tenant / user。
 */
public final class ToolContext {

    private final String runId;
    private final String traceId;
    private final String activeSkillId;
    private final String workspaceRoot;
    private final String sessionId;

    public ToolContext(String runId, String traceId) {
        this(runId, traceId, null);
    }

    public ToolContext(String runId, String traceId, String activeSkillId) {
        this(runId, traceId, activeSkillId, null);
    }

    public ToolContext(String runId, String traceId, String activeSkillId, String workspaceRoot) {
        this(runId, traceId, activeSkillId, workspaceRoot, null);
    }

    public ToolContext(String runId, String traceId, String activeSkillId, String workspaceRoot, String sessionId) {
        this.runId = runId;
        this.traceId = traceId;
        this.activeSkillId = StringUtils.hasText(activeSkillId) ? activeSkillId.trim() : null;
        this.workspaceRoot = StringUtils.hasText(workspaceRoot) ? workspaceRoot.trim() : null;
        this.sessionId = StringUtils.hasText(sessionId) ? sessionId.trim() : null;
    }

    public static ToolContext from(NodeContext nodeContext) {
        return from(nodeContext, null);
    }

    public static ToolContext from(NodeContext nodeContext, GraphState state) {
        String active = null;
        String workspaceRoot = null;
        String sessionId = null;
        if (state != null) {
            Object raw = state.get(StateKeys.ACTIVE_SKILL_ID);
            if (raw instanceof String && StringUtils.hasText((String) raw)) {
                active = ((String) raw).trim();
            }
            Object ws = state.get(StateKeys.WORKSPACE_ROOT);
            if (ws instanceof String && StringUtils.hasText((String) ws)) {
                workspaceRoot = ((String) ws).trim();
            }
            Object sid = state.get(StateKeys.SESSION_ID);
            if (sid instanceof String && StringUtils.hasText((String) sid)) {
                sessionId = ((String) sid).trim();
            }
        }
        if (nodeContext == null) {
            return new ToolContext(null, null, active, workspaceRoot, sessionId);
        }
        return new ToolContext(
                nodeContext.getRunId(),
                nodeContext.getTraceId(),
                active,
                workspaceRoot,
                sessionId);
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

    /** 本 run 工作区绝对路径；可空（未注入时工具按配置根 + session/run 推导）。 */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public String getSessionId() {
        return sessionId;
    }
}

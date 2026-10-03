package com.xmut.forma.pi.agent.graph;

import com.xmut.forma.pi.agent.agent.TurnBinder;

/**
 * 图通用状态键常量。
 * 功能描述：约定 messages、system_prompt、tool 决策等键名。
 */
public final class StateKeys {

    private StateKeys() {}

    /** {@code String} — 本轮 sessionId（限流 / 审计）；由 Loop.input 写入。 */
    public static final String SESSION_ID = "session_id";

    /**
     * {@code List<Message>} — 持久化对话 transcript（不含缓存 system）。
     *
     * <p>对齐开源 pi：本轮 user / tool 结果 / 人工说明都直接写入本键；
     * {@code AgentTurnNode} 只读此列表发模型。
     */
    public static final String MESSAGES = "messages";

    /**
     * {@code String} — 已废弃的本轮 user 暂存键。
     *
     * @deprecated 本轮 user 直接写入 {@link #MESSAGES}。
     */
    @Deprecated
    public static final String USER_MESSAGE = "user_message";

    /** {@code String} — → system stable：SOUL.md。 */
    public static final String SOUL = "soul";

    /** {@code String} — → system stable：skills prompt。 */
    public static final String SKILLS = "skills";

    /**
     * {@code String} — 本轮已 resolve 的 skill id。
     *
     * <p>由 {@code DefaultAgent} / {@link TurnBinder} 写入；节点只读。
     */
    public static final String ACTIVE_SKILL_ID = "active_skill_id";

    /**
     * {@code String} — 本 run 工作区绝对路径（模型不可改）。
     *
     * <p>由 {@code DefaultAgent.prepare} 从 {@code TurnInput.workspaceRoot} 写入；
     * HITL resume 时由 {@code ResumeRequest.workspaceRoot} 覆盖写入（优先于 checkpoint）；
     * {@code ToolContext.from} 读出供沙箱工具 I/O。
     */
    public static final String WORKSPACE_ROOT = "workspace_root";

    /**
     * {@code String} — 本轮模型 useCase（取自 ActiveSkill.modelUseCase）。
     *
     * <p>由 {@link TurnBinder} 写入；{@code AgentTurnNode} 读入
     * {@code ModelRequest.useCase}；缺省回落 {@code pi.default}。
     */
    public static final String MODEL_USE_CASE = "model_use_case";

    /**
     * {@code String} — → system stable：tools 文本（与 {@link #SKILLS} 对称；非 {@link #AVAILABLE_TOOLS}）。
     */
    public static final String TOOLS = "tools";

    /**
     * @deprecated 使用 {@link #TOOLS}；保留同值别名以免旧引用编译失败。
     */
    @Deprecated
    public static final String TOOL_GUIDANCE = TOOLS;

    /**
     * {@code List<Skill>} — 本轮可用 Skill 资产（与 {@link #AVAILABLE_TOOLS} 对称）。
     *
     * <p>EXPLICIT resolve 成功 → 单元素（当前 Active）；无 Active 且已注入 SkillCatalog
     * → {@code skillConfig.all()} 全量目录（供 51-10 Router）；无 SkillCatalog → 不写。
     */
    public static final String AVAILABLE_SKILLS = "available_skills";

    /** {@code String} — → system context：AGENTS.md。 */
    public static final String AGENTS = "agents";

    /** {@code String} — → system context：HERMES.md。 */
    public static final String HERMES = "hermes";

    /**
     * {@code String} — 预留：system stable 的 core 段键名（Memory 层暂未接入）。
     */
    public static final String CORE = "core";

    /** {@code String} — 预留：system variable 的 recall 段键名（Memory 层暂未接入）。 */
    public static final String MEMORY = "memory";

    /** {@code String} — 预留：system variable 的 user 段键名（Memory 层暂未接入）。 */
    public static final String USER = "user";

    /**
     * {@code String} — 入图前已 format 好的 system 全文。
     *
     * <p>{@code AgentTurnNode} 只认此键；由 {@code DefaultAgent} 入图前经
     * {@code SystemPromptInput.format()} 写好。
     */
    public static final String SYSTEM_PROMPT = "system_prompt";

    /**
     * @deprecated 使用 {@link #SYSTEM_PROMPT}。
     */
    @Deprecated
    public static final String SYSTEM_MESSAGE = "system_message";

    /**
     * {@code String} — PromptBuilder 原料（context：页面上下文）。入图前编进 {@link #SYSTEM_PROMPT}。
     */
    public static final String PAGE_CONTEXT = "page_context";

    /**
     * {@code String} — PromptBuilder 原料（variable：{@code before_agent_start} 增量）。
     * 入图前编进 {@link #SYSTEM_PROMPT}。
     */
    public static final String VOLATILE_EXTRA = "volatile_extra";

    /** LLM 最近一次响应文本（或结构化对象；本故事以 String 为主） */
    public static final String LLM_RESPONSE = "llm_response";

    /** {@code List<ToolCallEntry>} — 待执行的工具调用 */
    public static final String TOOL_CALLS = "tool_calls";

    /**
     * {@code List<ToolResult>} — 工具执行结果暂存。
     *
     * <p>ToolNode / ToolPolicyExtension 执行后应立刻并入 {@link #MESSAGES} 并清空；
     * AgentTurn 不再从此键回灌。
     */
    public static final String TOOL_RESULTS = "tool_results";

    /** 人工输入（resume / HITL；说明 / 拒绝原因） */
    public static final String HUMAN_INPUT = "human_input";

    /**
     * {@link com.xmut.forma.pi.agent.tool.ToolDecision} 或字符串 — WRITE HITL 批准决策。
     *
     * <p>由 {@code DefaultAgent.resume} 写入；{@code ToolPolicyExtension} 一次性消费后清空。
     */
    public static final String TOOL_APPROVAL = "tool_approval";

    /**
     * {@code String} — {@code ToolPolicyExtension} 闸门路由：
     * {@code needs_hitl} / {@code execute} / {@code agent}。
     *
     * <p>{@code needs_hitl} 由 {@code tools} 节点转成 {@link #INTERRUPT}。
     */
    public static final String TOOL_POLICY_ROUTE = "tool_policy_route";

    /**
     * {@code Boolean} — 节点执行后请求挂起；不写入 checkpoint。
     *
     * <p>resume 重跑同一节点（例如 {@code tools} 消费 {@link #TOOL_APPROVAL}）。
     */
    public static final String INTERRUPT = "__interrupt__";

    /**
     * {@code List<ToolSchema>} — 本轮可供模型选择的工具 schema。
     *
     * <p>由 Loop / ToolCatalog 写入 state；{@code AgentTurnNode} 读入 {@code ModelRequest.tools}。
     * 缺省或空 → 不带 tools。
     */
    public static final String AVAILABLE_TOOLS = "available_tools";

    /**
     * {@code Collection<String>} — 本轮已激活的工具名（来自 skill {@code allowedTools}）。
     *
     * <p>null / 未写 = 不按名限制（只拦未注册）；empty = 拒绝全部 tool_call。
     */
    public static final String ACTIVE_TOOLS = "active_tools";

    /**
     * {@code String} — 当前 agent turn id（一次 LLM hop）。
     *
     * <p>由 {@code AgentTurnNode} 写入；随后 {@code ToolNode} 读出，给 TOOL_EXECUTION_* 打同一 turnId。
     */
    public static final String CURRENT_TURN_ID = "current_turn_id";
}

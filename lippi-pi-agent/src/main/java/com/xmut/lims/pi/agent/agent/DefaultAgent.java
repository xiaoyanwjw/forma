package com.xmut.lims.pi.agent.agent;

import com.xmut.lims.pi.agent.TurnInput;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.IterationBudget;
import com.xmut.lims.pi.agent.ResumeRequest;
import com.xmut.lims.pi.agent.graph.CompileConfig;
import com.xmut.lims.pi.agent.graph.CompiledGraph;
import com.xmut.lims.pi.agent.graph.GraphOutcome;
import com.xmut.lims.pi.agent.graph.GraphState;
import com.xmut.lims.pi.agent.graph.RunnableConfig;
import com.xmut.lims.pi.agent.graph.StateGraph;
import com.xmut.lims.pi.agent.graph.StateKeys;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpoint;
import com.xmut.lims.pi.agent.graph.checkpoint.Checkpointer;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.skill.ActiveSkill;
import com.xmut.lims.pi.agent.skill.SkillCatalog;
import com.xmut.lims.pi.agent.skill.SkillSelector;
import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 默认实现。
 * 功能描述：运行模块内默认 Tool-loop 图（START → agent ⇄ tools → END）。
 * 关键设计：不按 Skill 换图；resume 支持 WRITE 批准与 ask_human（user 消息）双路径；幂等靠 confirmId。
 */
public final class DefaultAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);

    private static final int DEFAULT_MAX_SUPERSTEPS = 25;
    private static final Duration DEFAULT_OVERALL_TIMEOUT = Duration.ofSeconds(300);

    private final StateGraph stateGraph;
    private final Checkpointer checkpointer;
    private final ResumeIdempotencyStore resumeIdempotencyStore;
    private final IterationBudget budget;
    private final ToolCatalog toolConfig;
    private final SkillCatalog skillConfig;
    private final ConcurrentHashMap<String, CancelHandle> activeRuns = new ConcurrentHashMap<>();

    /**
     * 全参装配。{@code skillConfig} 可为 null（无 Skill 约束）；其余均必填。
     */
    public DefaultAgent(StateGraph stateGraph,
                        Checkpointer checkpointer,
                        ResumeIdempotencyStore resumeIdempotencyStore,
                        IterationBudget budget,
                        ToolCatalog toolConfig,
                        SkillCatalog skillConfig) {
        this.stateGraph = Objects.requireNonNull(stateGraph, "stateGraph");
        this.checkpointer = Objects.requireNonNull(checkpointer, "checkpointer");
        this.resumeIdempotencyStore = Objects.requireNonNull(resumeIdempotencyStore, "resumeIdempotencyStore");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.toolConfig = Objects.requireNonNull(toolConfig, "toolConfig");
        this.skillConfig = skillConfig;
    }

    @Override
    public ConversationResult run(TurnInput turnInput) {
        return run(turnInput, null);
    }

    /**
     * 首跑：编译 Tool-loop 图并 {@code invoke}。
     *
     * @param turnInput 本轮输入（messages / skill / context）
     * @param emitter   可选事件出口；可为 null
     * @return 图终态映射后的 {@link ConversationResult}
     */
    @Override
    public ConversationResult run(TurnInput turnInput, Emitter emitter) {
        CancelHandle handle = new CancelHandle();
        String runId = getRunId(turnInput);

        try {
            // 1. 占住 runId（同 run 并发直接失败）
            if (activeRuns.putIfAbsent(runId, handle) != null) {
                return ConversationResult.failed(runId, "run already active: " + runId);
            }

            // 2. 编译图 + 组装初始 state / RunnableConfig
            CompiledGraph compiled = stateGraph.compile(CompileConfig.builder()
                    .checkpointer(checkpointer)
                    .maxSupersteps(budget.maxTotal())
                    .overallTimeout(mapOverallTimeout(budget))
                    .build());

            Map<String, Object> input = prepare(turnInput, toolConfig, skillConfig);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .runId(runId)
                    .traceId(turnInput.getTraceId())
                    .cancelSignal(handle.flag::get)
                    .cancelReason(handle.reason)
                    .emitter(emitter)
                    .build();

            // 3. invoke → 映射 GraphOutcome（SUSPENDED 保留 checkpoint）
            GraphOutcome outcome = compiled.invoke(input, runnableConfig);
            return mapOutcome(runId, outcome);
        } catch (Exception ex) {
            return ConversationResult.failed(runId, ex.getMessage());
        } finally {
            activeRuns.remove(runId, handle);
        }
    }

    public Map<String, Object> prepare(TurnInput turnInput,
                                       ToolCatalog toolConfig,
                                       SkillCatalog skillConfig) {
        Objects.requireNonNull(turnInput, "turnInput required");

        Map<String, Object> input = new HashMap<>();

        // prepare system prompt
        ActiveSkill active = SkillSelector.select(turnInput, skillConfig);
        TurnBindings bindings = TurnBinder.bind(toolConfig, skillConfig, active);
        bindings.applyTo(input);

        final SystemPromptInput in = SystemPromptInput.builder()
                .stable(SystemPromptInput.mapOf(
                        SystemPromptInput.SKILLS, textOrNull(bindings.getSkillsText()),
                        SystemPromptInput.TOOLS, textOrNull(bindings.getToolsText())))
                .context(SystemPromptInput.mapOf(
                        SystemPromptInput.CONTEXT, textOrNull(turnInput.getContext())))
                .apply(turnInput.getContextModifier())
                .build();

        input.put(StateKeys.SYSTEM_PROMPT, in.format());

        // prepare messages / sessionId
        List<Message> history = new ArrayList<>();
        for (Message m : turnInput.getMessages()) {
            if (m == null) {
                continue;
            }
            if ("system".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            history.add(m);
        }
        input.put(StateKeys.MESSAGES, history);
        if (StringUtils.hasText(turnInput.getSessionId())) {
            input.put(StateKeys.SESSION_ID, turnInput.getSessionId().trim());
        }
        if (StringUtils.hasText(turnInput.getWorkspaceRoot())) {
            input.put(StateKeys.WORKSPACE_ROOT, turnInput.getWorkspaceRoot().trim());
        }

        return input;
    }

    ConversationResult mapOutcome(String runId, GraphOutcome outcome) {
        if (outcome == null) {
            cleanup(runId);
            return ConversationResult.failed(runId, "null GraphOutcome");
        }
        switch (outcome.getKind()) {
            case SUCCESS:
                cleanup(runId);
                return mapSuccess(runId, outcome.getFinalState());
            case FAILED:
                cleanup(runId);
                return ConversationResult.failed(runId,
                        outcome.getErrorMessage() != null ? outcome.getErrorMessage() : "graph failed");
            case CANCELLED:
                cleanup(runId);
                return ConversationResult.cancelled(runId,
                        outcome.getCancelReason() != null ? outcome.getCancelReason() : "cancelled");
            case SUSPENDED:
                // 保留 checkpoint 供 resume；终稿带回助手正文（如策划 JSON），勿只写 suspend 原因。
                GraphState suspendedState = outcome.getFinalState();
                String suspendedText = resolveResponse(suspendedState);
                if (!StringUtils.hasText(suspendedText)) {
                    String node = outcome.getSuspendedNode();
                    suspendedText = node != null ? "suspended at node: " + node : "suspended";
                }
                return ConversationResult.builder()
                        .runId(runId)
                        .status(ConversationResult.Status.SUSPENDED)
                        .finalResponse(suspendedText)
                        .messages(resolve(suspendedState))
                        .build();
            default:
                cleanup(runId);
                return ConversationResult.failed(runId, "unknown GraphOutcome kind: " + outcome.getKind());
        }
    }

    private static ConversationResult mapSuccess(String runId, GraphState state) {
        if (state == null) {
            return ConversationResult.ok(runId, null, Collections.emptyList());
        }
        String response = resolveResponse(state);
        List<Message> messages = resolve(state);
        return ConversationResult.ok(runId, response, messages);
    }

    @SuppressWarnings("unchecked")
    static String resolveResponse(GraphState state) {
        Object raw = state.get(StateKeys.MESSAGES);
        if (raw instanceof List) {
            List<Message> messages = (List<Message>) raw;
            // 优先：最后一条带 view 的助手正文（终态 JSON），避免 LLM_RESPONSE 停在中间轮叙述
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m != null && "assistant".equalsIgnoreCase(m.getRole()) && m.getContent() != null
                        && m.getContent().contains("\"view\"")) {
                    return m.getContent();
                }
            }
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m != null && "assistant".equalsIgnoreCase(m.getRole()) && m.getContent() != null) {
                    Object llm = state.get(StateKeys.LLM_RESPONSE);
                    if (llm instanceof String && StringUtils.hasText((String) llm)) {
                        return (String) llm;
                    }
                    return m.getContent();
                }
            }
        }
        Object llm = state.get(StateKeys.LLM_RESPONSE);
        if (llm instanceof String && StringUtils.hasText((String) llm)) {
            return (String) llm;
        }
        return llm != null ? String.valueOf(llm) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> resolve(GraphState state) {
        Object raw = state.get(StateKeys.MESSAGES);
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        return (List<Message>) raw;
    }

    /**
     * 终态清理 checkpoint（SUSPENDED 不调用）。
     * 幂等摘要<strong>不</strong>在此删除——须保留至 TTL，以便重复 {@code (runId, confirmId)}
     * 返回缓存结果（AC3）；显式 {@link ResumeIdempotencyStore#deleteByRun} 留给运维/覆盖写。
     */
    private void cleanup(String runId) {
        if (runId != null) {
            checkpointer.deleteByRun(runId);
        }
    }

    @Override
    public ConversationResult resume(ResumeRequest request) {
        return resume(request, null);
    }

    /**
     * HITL 续跑：confirmId 幂等占位 → tool-result / WRITE 双路径 → {@code compiled.resume}。
     *
     * @param request 续跑请求（须含 runId；toolCallId+result 与 WRITE decision 互斥）
     * @param emitter 可选事件出口；可为 null
     * @return 图终态映射后的 {@link ConversationResult}
     */
    @Override
    public ConversationResult resume(ResumeRequest request, Emitter emitter) {
        if (!StringUtils.hasText(request.getRunId())) {
            return ConversationResult.failed(null, "resume requires runId");
        }

        CancelHandle handle = new CancelHandle();
        String runId = request.getRunId().trim();
        String confirmId = request.getConfirmId();

        boolean claimed = false;
        try {
            // 1. 占住 runId
            if (activeRuns.putIfAbsent(runId, handle) != null) {
                return ConversationResult.failed(runId, "run already active: " + runId);
            }

            // 2. confirmId 幂等：COMPLETED/IN_PROGRESS 短路；CLAIMED 继续
            Claim claim = claim(runId, confirmId);
            if (claim.result != null) {
                return claim.result;
            }
            claimed = claim.claimed;

            // 3. 解析续跑模式（tool-result vs WRITE）；非法则 fail-closed
            ResumeResult resumeResult = resolveResumeResult(request, runId);
            if (resumeResult.invalid != null) {
                complete(claimed, runId, confirmId, resumeResult.invalid);
                return resumeResult.invalid;
            }

            // 4. 组装 resume input
            final Map<String, Object> input;
            if (resumeResult.toolResultMode) {
                try {
                    input = prepareToolResult(request, runId);
                } catch (IllegalArgumentException ex) {
                    ConversationResult failed = ConversationResult.failed(runId, ex.getMessage());
                    complete(claimed, runId, confirmId, failed);
                    return failed;
                }
            } else {
                input = prepareWrite(request, resumeResult.decision, runId);
            }

            // 5. 编译图并 resume
            CompiledGraph compiled = stateGraph.compile(CompileConfig.builder()
                    .checkpointer(checkpointer)
                    .maxSupersteps(budget.maxTotal())
                    .overallTimeout(mapOverallTimeout(budget))
                    .build());

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .runId(runId)
                    .traceId(request.getTraceId())
                    .cancelSignal(handle.flag::get)
                    .cancelReason(handle.reason)
                    .emitter(emitter)
                    .build();

            GraphOutcome outcome = compiled.resume(input, runnableConfig);
            ConversationResult result = mapOutcome(runId, outcome);

            // 6. 收敛幂等占位：再挂起 abandon；其余终态 complete
            complete(claimed, runId, confirmId, result);
            return result;
        } catch (RuntimeException ex) {
            try {
                complete(claimed, runId, confirmId, null);
            } catch (RuntimeException abandonEx) {
                log.warn("resume abandon after failure failed runId={}: {}", runId, abandonEx.toString());
            }
            log.error("resume failed runId={}: {}", runId, ex.toString());
            return ConversationResult.failed(runId, ex.getMessage());
        } finally {
            activeRuns.remove(runId, handle);
        }
    }

    /**
     * 幂等占位入口。
     * 功能描述：无 confirmId 则跳过；COMPLETED/IN_PROGRESS 经 {@link Claim#result} 短路返回。
     */
    private Claim claim(String runId, String confirmId) {
        if (confirmId == null) {
            return Claim.skipped();
        }

        ResumeIdempotencyStore.ClaimResult claim = resumeIdempotencyStore.claim(runId, confirmId);
        switch (claim.getStatus()) {
            case COMPLETED:
                ConversationResult cached = claim.getCompletedResult() != null
                        ? claim.getCompletedResult()
                        : ConversationResult.failed(runId, "idempotent resume: empty cached result");
                return Claim.completed(cached);
            case IN_PROGRESS:
                return Claim.completed(ConversationResult.failed(runId,
                        "resume already in progress for confirmId=" + confirmId));
            case CLAIMED:
                return Claim.claimed();
            default:
                return Claim.completed(ConversationResult.failed(runId, "unknown claim status"));
        }
    }

    /**
     * 解析 resume 双模式（tool-result vs WRITE）。
     * 功能描述：互斥或都缺时 {@link ResumeResult#invalid} 非空（fail-closed）。
     */
    private static ResumeResult resolveResumeResult(ResumeRequest request, String runId) {
        boolean toolResultMode = StringUtils.hasText(request.getToolCallId());
        ToolDecision decision = resolveDecision(request);
        if (toolResultMode && decision != null) {
            return ResumeResult.invalid(ConversationResult.failed(runId,
                    "resume tool-result and WRITE decision are mutually exclusive"));
        }
        if (!toolResultMode && decision == null) {
            return ResumeResult.invalid(ConversationResult.failed(runId,
                    "resume requires toolCallId+result or decision (APPROVE|DENY)/approved"));
        }
        return ResumeResult.ok(toolResultMode, decision);
    }

    /**
     * 收敛 claim 后的 store complete/abandon 样板。
     * 功能描述：未 claim 则 no-op；SUSPENDED 或异常（result==null）→ store.abandon；其余终态 → store.complete。
     */
    private void complete(boolean claimed, String runId, String confirmId, ConversationResult result) {
        if (!claimed) {
            return;
        }
        if (result == null || result.getStatus() == ConversationResult.Status.SUSPENDED) {
            resumeIdempotencyStore.abandon(runId, confirmId);
        } else {
            resumeIdempotencyStore.complete(runId, confirmId, result);
        }
    }

    /** {@link #claim} 结果：{@code result} 非空则立即返回；否则看 {@code claimed}。 */
    private static final class Claim {
        final boolean claimed;
        final ConversationResult result;

        private Claim(boolean claimed, ConversationResult result) {
            this.claimed = claimed;
            this.result = result;
        }

        static Claim skipped() {
            return new Claim(false, null);
        }

        static Claim claimed() {
            return new Claim(true, null);
        }

        static Claim completed(ConversationResult result) {
            return new Claim(false, result);
        }
    }

    /** {@link #resolveResumeResult} 结果：{@code invalid} 非空则 fail-closed。 */
    private static final class ResumeResult {
        final boolean toolResultMode;
        final ToolDecision decision;
        final ConversationResult invalid;

        private ResumeResult(boolean toolResultMode, ToolDecision decision, ConversationResult invalid) {
            this.toolResultMode = toolResultMode;
            this.decision = decision;
            this.invalid = invalid;
        }

        static ResumeResult ok(boolean toolResultMode, ToolDecision decision) {
            return new ResumeResult(toolResultMode, decision, null);
        }

        static ResumeResult invalid(ConversationResult failed) {
            return new ResumeResult(false, null, failed);
        }
    }

    static ToolDecision resolveDecision(ResumeRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getDecision() != null) {
            return request.getDecision();
        }
        if (request.getApproved() == null) {
            return null;
        }
        return request.getApproved() ? ToolDecision.APPROVE : ToolDecision.DENY;
    }

    /** WRITE 批准路径：写入 TOOL_APPROVAL，可选把 humanInput 追加为 user 消息。 */
    private Map<String, Object> prepareWrite(ResumeRequest request, ToolDecision decision, String runId) {
        Map<String, Object> input = new HashMap<>();
        input.put(StateKeys.TOOL_APPROVAL, decision);
        if (StringUtils.hasText(request.getHumanInput())) {
            String note = request.getHumanInput();
            input.put(StateKeys.HUMAN_INPUT, note);

            // 对齐开源 pi：人工说明直接进 transcript（policy 仍可读 HUMAN_INPUT 作拒绝原因）
            Checkpoint latest = checkpointer.loadLatest(runId).orElse(null);
            if (latest != null && latest.getState() != null) {
                List<Message> messages = Message.copyFrom(latest.getState().get(StateKeys.MESSAGES));
                Message.append(messages, null, null, Message.user(note));
                input.put(StateKeys.MESSAGES, messages);
            }
        }
        return input;
    }

    /**
     * ask_human 续跑：选项/自由文本作为 user 消息追加；tool 回执已在 interrupt 时写入。
     * 清空 TOOL_CALLS，resume 重入 tools 后无 call → 边到 agent。
     *
     * @throws IllegalArgumentException transcript 中无对应 tool 回执（未知 toolCallId）
     */
    private Map<String, Object> prepareToolResult(ResumeRequest request, String runId) {
        Map<String, Object> input = new HashMap<>();
        String toolCallId = request.getToolCallId().trim();
        String text = request.getHumanInput() != null ? request.getHumanInput() : "";

        Checkpoint latest = checkpointer.loadLatest(runId).orElse(null);
        if (latest == null || latest.getState() == null) {
            input.put(StateKeys.TOOL_CALLS, Collections.emptyList());
            return input;
        }

        GraphState state = latest.getState();
        List<Message> messages = Message.copyFrom(state.get(StateKeys.MESSAGES));
        if (!hasToolReply(messages, toolCallId)) {
            throw new IllegalArgumentException("unknown toolCallId: " + toolCallId);
        }
        Message.append(messages, null, null, Message.user(text));
        input.put(StateKeys.MESSAGES, messages);
        // 丢弃 interrupt 时未跑完的后续 call；人答之后先回 agent
        input.put(StateKeys.TOOL_CALLS, Collections.emptyList());
        return input;
    }

    private static boolean hasToolReply(List<Message> messages, String toolCallId) {
        if (messages == null || !StringUtils.hasText(toolCallId)) {
            return false;
        }
        for (Message m : messages) {
            if (m != null && "tool".equalsIgnoreCase(m.getRole())
                    && toolCallId.equals(m.getToolCallId())) {
                return true;
            }
        }
        return false;
    }

    static Duration mapOverallTimeout(IterationBudget budget) {
        if (budget == null || budget.overallTimeoutMs() == null) {
            return DEFAULT_OVERALL_TIMEOUT;
        }
        long ms = budget.overallTimeoutMs();
        if (ms <= 0) {
            return DEFAULT_OVERALL_TIMEOUT;
        }
        return Duration.ofMillis(ms);
    }

    @Override
    public void cancel(String runId, String reason) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        String key = runId.trim();
        CancelHandle handle = activeRuns.get(key);
        if (handle != null) {
            handle.reason.set(reason);
            handle.flag.set(true);
            log.debug("ConversationLoop.cancel requested runId={} reason={}", key, reason);
        } else {
            log.debug("ConversationLoop.cancel no active run runId={} reason={}", key, reason);
        }
    }

    private static String textOrNull(String raw) {
        return StringUtils.hasText(raw) ? raw : null;
    }

    /** Blank / null runId → 新 UUID（ConcurrentHashMap 禁止 null key）。 */
    static String getRunId(TurnInput turnInput) {
        if (turnInput != null && StringUtils.hasText(turnInput.getRunId())) {
            return turnInput.getRunId().trim();
        }
        return UUID.randomUUID().toString();
    }

    private static final class CancelHandle {
        private final AtomicBoolean flag = new AtomicBoolean(false);
        private final AtomicReference<String> reason = new AtomicReference<>();
    }
}

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
import com.xmut.lims.pi.ai.tool.ToolCallEntry;
import com.xmut.lims.pi.ai.tool.ToolResult;
import com.xmut.lims.pi.agent.skill.ActiveSkill;
import com.xmut.lims.pi.agent.skill.SkillConfig;
import com.xmut.lims.pi.agent.skill.SkillSelector;
import com.xmut.lims.pi.agent.event.Emitter;
import com.xmut.lims.pi.agent.tool.ToolDecision;
import com.xmut.lims.pi.agent.tool.ToolConfig;
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
 * 关键设计：不按 Skill 换图；resume 支持 WRITE 批准与 tool-result 注入双路径；幂等靠 confirmRequestId。
 */
public final class DefaultAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);

    private static final int DEFAULT_MAX_SUPERSTEPS = 25;
    private static final Duration DEFAULT_OVERALL_TIMEOUT = Duration.ofSeconds(300);

    private final StateGraph stateGraph;
    private final Checkpointer checkpointer;
    private final ResumeIdempotencyStore resumeIdempotencyStore;
    private final IterationBudget budget;
    private final ToolConfig toolConfig;
    private final SkillConfig skillConfig;
    private final ConcurrentHashMap<String, CancelHandle> activeRuns = new ConcurrentHashMap<>();

    /**
     * 全参装配。{@code skillConfig} 可为 null（无 Skill 约束）；其余均必填。
     */
    public DefaultAgent(StateGraph stateGraph,
                        Checkpointer checkpointer,
                        ResumeIdempotencyStore resumeIdempotencyStore,
                        IterationBudget budget,
                        ToolConfig toolConfig,
                        SkillConfig skillConfig) {
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

    @Override
    public ConversationResult run(TurnInput turnInput, Emitter emitter) {
        if (turnInput == null) {
            return ConversationResult.failed(null, "request required");
        }

        CancelHandle handle = new CancelHandle();
        String runId = getRunId(turnInput);

        try {
            if (activeRuns.putIfAbsent(runId, handle) != null) {
                return ConversationResult.failed(runId, "run already active: " + runId);
            }

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

            GraphOutcome outcome = compiled.invoke(input, runnableConfig);
            return mapOutcome(runId, outcome);
        } catch (IllegalArgumentException ex) {
            return ConversationResult.failed(runId, ex.getMessage());
        } finally {
            activeRuns.remove(runId, handle);
        }
    }

    public Map<String, Object> prepare(TurnInput turnInput,
                                       ToolConfig toolConfig,
                                       SkillConfig skillConfig) {
        Objects.requireNonNull(turnInput, "turn");

        Map<String, Object> input = new HashMap<>();
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
                // 保留 checkpoint 供 resume
                String node = outcome.getSuspendedNode();
                return ConversationResult.suspended(runId,
                        node != null ? "suspended at node: " + node : "suspended");
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
        Object llm = state.get(StateKeys.LLM_RESPONSE);
        if (llm instanceof String && StringUtils.hasText((String) llm)) {
            return (String) llm;
        }
        Object raw = state.get(StateKeys.MESSAGES);
        if (raw instanceof List) {
            List<Message> messages = (List<Message>) raw;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m != null && "assistant".equalsIgnoreCase(m.getRole()) && m.getContent() != null) {
                    return m.getContent();
                }
            }
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
     * 幂等摘要<strong>不</strong>在此删除——须保留至 TTL，以便重复 {@code (runId, confirmRequestId)}
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

    @Override
    public ConversationResult resume(ResumeRequest request, Emitter emitter) {
        if (request == null || !StringUtils.hasText(request.getRunId())) {
            return ConversationResult.failed(null, "resume requires runId");
        }

        CancelHandle handle = new CancelHandle();
        String runId = request.getRunId().trim();
        String confirmId = request.getConfirmRequestId();

        boolean claimed = false;
        try {
            if (activeRuns.putIfAbsent(runId, handle) != null) {
                return ConversationResult.failed(runId, "run already active: " + runId);
            }

            Claim claim = claim(runId, confirmId);
            if (claim.result != null) {
                return claim.result;
            }
            
            claimed = claim.claimed;

            ResumeResult resumeResult = resolveResumeResult(request, runId);
            if (resumeResult.invalid != null) {
                complete(claimed, runId, confirmId, resumeResult.invalid);
                return resumeResult.invalid;
            }

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

            // 再次挂起：abandon 释放占位；其余终态（含 fail-closed）→ complete
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
                        "resume already in progress for confirmRequestId=" + confirmId));
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
     * tool-result 路径：合成 ToolResult 进 transcript，从挂起 TOOL_CALLS 摘掉该 call，不再执行 handler。
     *
     * @throws IllegalArgumentException 未知 toolCallId（文案含 toolCallId/unknown）；调用方勿 resume 图
     */
    private Map<String, Object> prepareToolResult(ResumeRequest request, String runId) {
        Map<String, Object> input = new HashMap<>();
        String toolCallId = request.getToolCallId().trim();
        String output = request.getHumanInput() != null ? request.getHumanInput() : "";

        Checkpoint latest = checkpointer.loadLatest(runId).orElse(null);
        if (latest == null || latest.getState() == null) {
            // GraphExecutor 会以「No checkpoint」失败；此处仍组装最小 input 保持路径一致
            input.put(StateKeys.TOOL_CALLS, Collections.emptyList());
            return input;
        }

        GraphState state = latest.getState();
        ToolCallEntry matched = findToolCall(state.get(StateKeys.TOOL_CALLS), toolCallId);
        if (matched == null) {
            throw new IllegalArgumentException("unknown toolCallId: " + toolCallId);
        }
        ToolResult injected = ToolResult.ok(toolCallId, matched.getToolName(), output);
        input.put(StateKeys.MESSAGES,
                Message.withToolResults(state.get(StateKeys.MESSAGES),
                        Collections.singletonList(injected)));
        input.put(StateKeys.TOOL_CALLS, removeToolCall(state.get(StateKeys.TOOL_CALLS), toolCallId));
        return input;
    }

    private static ToolCallEntry findToolCall(Object rawCalls, String toolCallId) {
        if (!(rawCalls instanceof List)) {
            return null;
        }
        for (Object item : (List<?>) rawCalls) {
            if (item instanceof ToolCallEntry) {
                ToolCallEntry call = (ToolCallEntry) item;
                if (toolCallId.equals(call.getId())) {
                    return call;
                }
            }
        }
        return null;
    }

    private static List<ToolCallEntry> removeToolCall(Object rawCalls, String toolCallId) {
        if (!(rawCalls instanceof List)) {
            return Collections.emptyList();
        }
        List<ToolCallEntry> remaining = new ArrayList<>();
        for (Object item : (List<?>) rawCalls) {
            if (!(item instanceof ToolCallEntry)) {
                continue;
            }
            ToolCallEntry call = (ToolCallEntry) item;
            if (toolCallId.equals(call.getId())) {
                continue;
            }
            remaining.add(call);
        }
        return remaining;
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

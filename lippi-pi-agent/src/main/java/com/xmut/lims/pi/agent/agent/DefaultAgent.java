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
 * ConversationLoop 默认实现：唯一控制流 = hermes 内<strong>一张</strong> StateGraph（默认 Tool-loop）。
 *
 * <p>不存在 Planner/StepHandler；不委托 agent Graph / AgentRuntime。
 * 不按 Skill topology 换图——Skill 通过 whitelist / useCase / skills 文本约束行为
 * （如 OCR 空 whitelist → 不暴露 tools；未知 tool_calls → tools 节点闸门 fail-closed，不进 HITL）。
 *
 * <p>WRITE HITL：{@code tools} 节点在执行前申请挂起；resume 映射 {@link ToolDecision} →
 * {@link StateKeys#TOOL_APPROVAL} 后重跑同一节点。[LIMS] HITL ≠ 上游 Session {@code /resume}。
 *
 * <p>overallTimeout：分段（segment）—— resume 从 now 重算，HITL 等待不消耗执行预算。
 *
 * <p>resume 幂等：非空 {@link ResumeRequest#getConfirmRequestId()} 经
 * {@link ResumeIdempotencyStore} 原子占位；重复 APPROVE/DENY 不重入 WRITE handler。
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
            cleanupTerminal(runId);
            return ConversationResult.failed(runId, "null GraphOutcome");
        }
        switch (outcome.getKind()) {
            case SUCCESS:
                cleanupTerminal(runId);
                return mapSuccess(runId, outcome.getFinalState());
            case FAILED:
                cleanupTerminal(runId);
                return ConversationResult.failed(runId,
                        outcome.getErrorMessage() != null ? outcome.getErrorMessage() : "graph failed");
            case CANCELLED:
                cleanupTerminal(runId);
                return ConversationResult.cancelled(runId,
                        outcome.getCancelReason() != null ? outcome.getCancelReason() : "cancelled");
            case SUSPENDED:
                // 保留 checkpoint 供 resume
                String node = outcome.getSuspendedNode();
                return ConversationResult.suspended(runId,
                        node != null ? "suspended at node: " + node : "suspended");
            default:
                cleanupTerminal(runId);
                return ConversationResult.failed(runId, "unknown GraphOutcome kind: " + outcome.getKind());
        }
    }

    private static ConversationResult mapSuccess(String runId, GraphState state) {
        if (state == null) {
            return ConversationResult.ok(runId, null, Collections.emptyList());
        }
        String finalResponse = resolveFinalResponse(state);
        List<Message> messages = resolve(state);
        return ConversationResult.ok(runId, finalResponse, messages);
    }

    @SuppressWarnings("unchecked")
    static String resolveFinalResponse(GraphState state) {
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
    private void cleanupTerminal(String runId) {
        if (runId != null && checkpointer != null) {
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
        String runId = request.getRunId();
        String confirmId = request.getConfirmRequestId();

        boolean claimed = false;
        try {
            if (activeRuns.putIfAbsent(runId, handle) != null) {
                return ConversationResult.failed(runId, "run already active: " + runId);
            }

            if (confirmId != null) {
                ResumeIdempotencyStore.ClaimResult claim = resumeIdempotencyStore.claim(runId, confirmId);
                switch (claim.getStatus()) {
                    case COMPLETED:
                        return claim.getCompletedResult() != null
                                ? claim.getCompletedResult()
                                : ConversationResult.failed(runId, "idempotent resume: empty cached result");
                    case IN_PROGRESS:
                        return ConversationResult.failed(runId,
                                "resume already in progress for confirmRequestId=" + confirmId);
                    case CLAIMED:
                        claimed = true;
                        break;
                    default:
                        return ConversationResult.failed(runId, "unknown claim status");
                }
            }

            // WRITE HITL fail-closed：缺 APPROVE/DENY 禁止再挂起
            ToolDecision decision = resolveDecision(request);
            if (decision == null) {
                ConversationResult failed = ConversationResult.failed(runId,
                        "resume requires decision (APPROVE|DENY) or approved boolean");
                if (claimed) {
                    resumeIdempotencyStore.complete(runId, confirmId, failed);
                }
                return failed;
            }

            CompiledGraph compiled = stateGraph.compile(CompileConfig.builder()
                    .checkpointer(checkpointer)
                    .maxSupersteps(budget.maxTotal())
                    .overallTimeout(mapOverallTimeout(budget))
                    .build());

            final Map<String, Object> input = prepare(request, decision, runId);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .runId(runId)
                    .traceId(request.getTraceId())
                    .cancelSignal(handle.flag::get)
                    .cancelReason(handle.reason)
                    .emitter(emitter)
                    .build();

            GraphOutcome outcome = compiled.resume(input, runnableConfig);
            ConversationResult result = mapOutcome(runId, outcome);

            if (claimed) {
                if (result.getStatus() == ConversationResult.Status.SUSPENDED) {
                    // 再次挂起：释放占位，避免卡死后续同 confirmId 重试
                    resumeIdempotencyStore.abandon(runId, confirmId);
                } else {
                    resumeIdempotencyStore.complete(runId, confirmId, result);
                }
            }
            return result;
        } catch (RuntimeException ex) {
            if (claimed) {
                try {
                    resumeIdempotencyStore.abandon(runId, confirmId);
                } catch (RuntimeException abandonEx) {
                    log.warn("resume abandon after failure failed runId={}: {}", runId, abandonEx.toString());
                }
            }
            log.error("resume failed runId={}: {}", runId, ex.toString());
            return ConversationResult.failed(runId, ex.getMessage());
        } finally {
            activeRuns.remove(runId, handle);
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

    private Map<String, Object> prepare(ResumeRequest request, ToolDecision decision, String runId) {
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
